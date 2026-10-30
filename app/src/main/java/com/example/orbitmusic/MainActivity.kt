package com.example.orbitmusic

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.URLUtil
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState

data class LocalSong(val id: Long, val title: String, val artist: String, val uri: Uri) {
    override fun toString(): String = "$title  •  $artist"
}

class MainActivity : AppCompatActivity() {
    // Client ID is public app configuration; never put a client secret or access token here.
    private val spotifyClientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "orbitmusic://callback"
    private var spotifyRemote: SpotifyAppRemote? = null
    private var spotifyIsPaused = true
    private val songs = mutableListOf<LocalSong>()
    private val visibleSongs = mutableListOf<LocalSong>()
    private lateinit var songAdapter: ArrayAdapter<LocalSong>
    private var currentIndex = -1
    private var localPlayer: ExoPlayer? = null
    private lateinit var spotifyStatus: TextView
    private lateinit var localStatus: TextView
    private lateinit var currentSong: TextView

    companion object { private const val AUDIO_PERMISSION_REQUEST = 401 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        spotifyStatus = findViewById(R.id.spotifyStatus)
        localStatus = findViewById(R.id.localStatus)
        currentSong = findViewById(R.id.currentSong)
        localPlayer = ExoPlayer.Builder(this).build()

        songAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, visibleSongs)
        findViewById<ListView>(R.id.songList).apply {
            adapter = songAdapter
            setOnItemClickListener { _, _, position, _ ->
                val selected = visibleSongs[position]
                currentIndex = songs.indexOfFirst { it.id == selected.id }
                playLocal(selected)
            }
        }

        findViewById<Button>(R.id.spotifyConnect).setOnClickListener { connectSpotify() }
        findViewById<Button>(R.id.openPlaylist).setOnClickListener { playSpotifyPlaylist() }
        findViewById<Button>(R.id.spotifyToggle).setOnClickListener {
            val remote = spotifyRemote
            if (remote == null) toast("Connect to Spotify first")
            else if (spotifyIsPaused) remote.playerApi.resume() else remote.playerApi.pause()
        }
        findViewById<Button>(R.id.spotifyNext).setOnClickListener {
            spotifyRemote?.playerApi?.skipNext() ?: toast("Connect to Spotify first")
        }
        findViewById<Button>(R.id.spotifyPrevious).setOnClickListener {
            spotifyRemote?.playerApi?.skipPrevious() ?: toast("Connect to Spotify first")
        }
        findViewById<Button>(R.id.scanMusic).setOnClickListener { checkAudioPermissionAndLoad() }
        findViewById<EditText>(R.id.searchSongs).addTextChangedListener(SimpleTextWatcher { filterSongs(it) })
        findViewById<Button>(R.id.localToggle).setOnClickListener {
            val player = localPlayer ?: return@setOnClickListener
            if (player.isPlaying) player.pause() else player.play()
        }
        findViewById<Button>(R.id.localNext).setOnClickListener { playRelative(1) }
        findViewById<Button>(R.id.localPrevious).setOnClickListener { playRelative(-1) }
        findViewById<Button>(R.id.downloadAudio).setOnClickListener { downloadDirectAudio() }
    }

    private fun connectSpotify() {
        if (spotifyClientId == "YOUR_SPOTIFY_CLIENT_ID") {
            toast("Add your Spotify Client ID in MainActivity.kt first")
            return
        }
        val params = ConnectionParams.Builder(spotifyClientId)
            .setRedirectUri(redirectUri).showAuthView(true).build()
        SpotifyAppRemote.connect(this, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyRemote = appRemote
                spotifyStatus.text = "Connected to Spotify"
                appRemote.playerApi.subscribeToPlayerState()
                    .setEventCallback { state: PlayerState ->
                        spotifyIsPaused = state.isPaused
                        val track = state.track
                        spotifyStatus.text = if (track != null)
                            "Spotify: ${track.name} — ${track.artist.name}" else "Connected to Spotify"
                    }
                    .setErrorCallback { spotifyStatus.text = "Player state error" }
            }
            override fun onFailure(throwable: Throwable) {
                spotifyStatus.text = "Spotify connection failed"
                toast("Install Spotify, sign in, then try again")
            }
        })
    }

    private fun playSpotifyPlaylist() {
        val input = findViewById<EditText>(R.id.playlistUrl).text.toString().trim()
        val playlistUri = parseSpotifyPlaylistUri(input)
        if (playlistUri == null) { toast("Paste a valid Spotify playlist URL or URI"); return }
        val remote = spotifyRemote
        if (remote == null) { toast("Connect to Spotify first"); return }
        remote.playerApi.play(playlistUri)
            .setResultCallback { toast("Opening playlist in Spotify") }
            .setErrorCallback { toast("Could not play playlist") }
    }

    private fun parseSpotifyPlaylistUri(input: String): String? {
        val regex = Regex("(?:open\\.spotify\\.com/playlist/|spotify:playlist:)([A-Za-z0-9]+)")
        val id = regex.find(input)?.groupValues?.getOrNull(1) ?: return null
        return "spotify:playlist:$id"
    }

    private fun checkAudioPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) loadLocalSongs()
        else ActivityCompat.requestPermissions(this, arrayOf(permission), AUDIO_PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) loadLocalSongs()
        else if (requestCode == AUDIO_PERMISSION_REQUEST) toast("Audio permission is needed to list local music")
    }

    private fun loadLocalSongs() {
        songs.clear()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        contentResolver.query(collection, projection, selection, null, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown title"
                val artist = cursor.getString(artistCol) ?: "Unknown artist"
                songs.add(LocalSong(id, title, artist, Uri.withAppendedPath(collection, id.toString())))
            }
        }
        visibleSongs.clear()
        visibleSongs.addAll(songs)
        songAdapter.notifyDataSetChanged()
        localStatus.text = "${songs.size} songs found • sorted A–Z"
    }

    private fun filterSongs(query: String) {
        visibleSongs.clear()
        visibleSongs.addAll(songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) })
        songAdapter.notifyDataSetChanged()
        localStatus.text = "${visibleSongs.size} matching songs"
    }

    private fun playLocal(song: LocalSong) {
        val player = localPlayer ?: return
        player.setMediaItem(MediaItem.fromUri(song.uri))
        player.prepare()
        player.play()
        currentSong.text = "${song.title} — ${song.artist}"
    }

    private fun playRelative(delta: Int) {
        if (songs.isEmpty()) { toast("Load local songs first"); return }
        currentIndex = if (currentIndex < 0) 0 else (currentIndex + delta + songs.size) % songs.size
        playLocal(songs[currentIndex])
    }

    private fun downloadDirectAudio() {
        val url = findViewById<EditText>(R.id.audioUrl).text.toString().trim()
        if (!(url.startsWith("https://") || url.startsWith("http://")) || !URLUtil.isValidUrl(url)) {
            toast("Enter a valid direct HTTP(S) audio URL")
            return
        }
        val filename = URLUtil.guessFileName(url, null, null)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(filename)
            .setDescription("Downloading audio file")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        toast("Download started")
    }

    override fun onStop() {
        super.onStop()
        spotifyRemote?.let { SpotifyAppRemote.disconnect(it) }
        spotifyRemote = null
    }

    override fun onDestroy() {
        localPlayer?.release()
        localPlayer = null
        super.onDestroy()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
