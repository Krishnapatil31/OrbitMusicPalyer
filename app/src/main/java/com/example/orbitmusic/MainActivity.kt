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
import android.widget.LinearLayout
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

data class RemoteTrack(val id: String, val title: String, val artist: String)
data class LocalSong(val id: Long, val title: String, val artist: String, val uri: Uri) {
    override fun toString(): String = "$title  •  $artist"
}

class MainActivity : AppCompatActivity() {
    private val songs = mutableListOf<LocalSong>()
    private val visibleSongs = mutableListOf<LocalSong>()
    private lateinit var songAdapter: ArrayAdapter<LocalSong>
    private var currentIndex = -1
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var localPlayer: ExoPlayer? = null
    private lateinit var localStatus: TextView
    private lateinit var currentSong: TextView

    companion object { private const val AUDIO_PERMISSION_REQUEST = 401 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupNavigation()
        findViewById<View>(R.id.continueGuest).setOnClickListener { enterOrbit("Guest listener") }
        findViewById<Button>(R.id.loginGoogle).setOnClickListener { toast("Google sign-in needs Firebase project configuration before it can be enabled.") }
        findViewById<Button>(R.id.loginEmail).setOnClickListener { toast("Email login needs secure account authentication setup. It is not enabled yet.") }
        findViewById<Button>(R.id.signupEmail).setOnClickListener { toast("Email signup needs secure account authentication setup. It is not enabled yet.") }
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

        findViewById<Button>(R.id.scanMusic).setOnClickListener { checkAudioPermissionAndLoad() }
        findViewById<Button>(R.id.onlineSearchButton).setOnClickListener { searchOnlineSongs() }
        findViewById<Button>(R.id.localToggle).setOnClickListener {
            val player = localPlayer ?: return@setOnClickListener
            if (player.isPlaying) player.pause() else player.play()
        }
        findViewById<Button>(R.id.localNext).setOnClickListener { playRelative(1) }
        findViewById<Button>(R.id.localPrevious).setOnClickListener { playRelative(-1) }
        findViewById<Button>(R.id.downloadAudio).setOnClickListener { downloadDirectAudio() }
    }

    private fun enterOrbit(accountLabel: String) {
        findViewById<View>(R.id.authScreen).visibility = View.GONE
        findViewById<View>(R.id.mainScroll).visibility = View.VISIBLE
        findViewById<View>(R.id.bottomNavigation).visibility = View.VISIBLE
        findViewById<TextView>(R.id.pageTitle).text = "Good evening"
        toast("Welcome to Orbit Music · $accountLabel")
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.navHome).setOnClickListener { showPage("Home") }
        findViewById<View>(R.id.navSearch).setOnClickListener { showPage("Search") }
        findViewById<View>(R.id.navLibrary).setOnClickListener { showPage("Your Library") }
        findViewById<View>(R.id.navProfile).setOnClickListener { showPage("Profile") }
        findViewById<View>(R.id.profileButton).setOnClickListener { showPage("Profile") }
        findViewById<View>(R.id.settingsButton).setOnClickListener { showPage("Profile") }
        findViewById<View>(R.id.navPremium).setOnClickListener { toast("Orbit Premium plans are not configured yet.") }
    }

    private fun showPage(page: String) {
        findViewById<View>(R.id.homeContent).visibility = if (page == "Home") View.VISIBLE else View.GONE
        findViewById<View>(R.id.searchContent).visibility = if (page == "Search") View.VISIBLE else View.GONE
        findViewById<View>(R.id.libraryContent).visibility = if (page == "Your Library") View.VISIBLE else View.GONE
        findViewById<View>(R.id.profileContent).visibility = if (page == "Profile") View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.pageTitle).text = if (page == "Home") "Good evening" else page
        val active = when (page) {
            "Home" -> R.id.navHome
            "Search" -> R.id.navSearch
            "Your Library" -> R.id.navLibrary
            else -> R.id.navProfile
        }
        listOf(R.id.navHome, R.id.navSearch, R.id.navLibrary, R.id.navPremium, R.id.navProfile).forEach { id ->
            findViewById<TextView>(id).setTextColor(if (id == active) 0xFFFFFFFF.toInt() else 0xFFB3B3B3.toInt())
        }
        findViewById<android.widget.ScrollView>(R.id.mainScroll).smoothScrollTo(0, 0)
    }

    private fun searchOnlineSongs() {
        val query = findViewById<EditText>(R.id.searchSongs).text.toString().trim()
        if (query.length < 2) { toast("Type at least 2 letters to search online music"); return }
        val resultsView = findViewById<LinearLayout>(R.id.onlineResults)
        resultsView.removeAllViews()
        resultsView.addView(TextView(this).apply { text = "Searching Audius…"; setTextColor(0xFFB3B3B3.toInt()); setPadding(8, 18, 8, 18) })
        networkExecutor.execute {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val connection = URL("https://api.audius.co/v1/tracks/search?query=$encoded&limit=25").openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty("Accept", "application/json")
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val data = JSONObject(response).optJSONArray("data") ?: org.json.JSONArray()
                val tracks = (0 until data.length()).mapNotNull { index ->
                    val item = data.optJSONObject(index) ?: return@mapNotNull null
                    val id = item.optString("id")
                    if (id.isBlank() || id == "null") return@mapNotNull null
                    val artist = item.optJSONObject("user")?.optString("name") ?: "Unknown artist"
                    RemoteTrack(id, item.optString("title", "Unknown track"), artist)
                }
                mainHandler.post {
                    resultsView.removeAllViews()
                    if (tracks.isEmpty()) {
                        resultsView.addView(TextView(this).apply { text = "No tracks found. Try another search."; setTextColor(0xFFB3B3B3.toInt()); setPadding(8, 18, 8, 18) })
                    } else tracks.forEach { track ->
                        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(8, 12, 8, 12) }
                        val label = TextView(this).apply {
                            text = "${track.title}\n${track.artist}"
                            setTextColor(0xFFFFFFFF.toInt()); textSize = 15f
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        val play = Button(this).apply {
                            text = "PLAY"
                            setOnClickListener {
                                val stream = "https://api.audius.co/v1/tracks/${track.id}/stream"
                                localPlayer?.setMediaItem(MediaItem.fromUri(stream))
                                localPlayer?.prepare()
                                localPlayer?.play()
                                currentSong.text = "${track.title} — ${track.artist}"
                                toast("Playing from Audius")
                            }
                        }
                        row.addView(label); row.addView(play)
                        resultsView.addView(row)
                        resultsView.addView(View(this).apply { setBackgroundColor(0xFF333333.toInt()) },
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
                    }
                }
            } catch (error: Exception) {
                mainHandler.post {
                    resultsView.removeAllViews()
                    resultsView.addView(TextView(this).apply { text = "Online search failed. Check internet and try again."; setTextColor(0xFFFF8A80.toInt()); setPadding(8, 18, 8, 18) })
                }
            }
        }
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
        if (!url.startsWith("https://", ignoreCase = true) || !URLUtil.isValidUrl(url)) {
            toast("Enter a secure HTTPS direct audio link")
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
    }

    override fun onDestroy() {
        networkExecutor.shutdownNow()
        localPlayer?.release()
        localPlayer = null
        super.onDestroy()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
