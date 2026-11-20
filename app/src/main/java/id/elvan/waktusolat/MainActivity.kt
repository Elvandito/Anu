package id.elvan.waktusolat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import id.elvan.waktusolat.data.*
import id.elvan.waktusolat.notify.PrayerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private val Forest = Color(0xFF123D32)
private val Green = Color(0xFF1F6B52)
private val Mint = Color(0xFFE7F1E9)
private val Cream = Color(0xFFF7F5ED)
private val Gold = Color(0xFFC49A50)

class MainActivity : ComponentActivity() {
    private lateinit var repo: PrayerRepository
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { loadLocationAndRefresh() }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = PrayerRepository(this)
        PrayerScheduler.ensureChannel(this)
        createDailySync()
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { SolatApp(repo, onRefresh = { refreshSchedule(false) }, onGps = { requestLocation() }, onSound = { openSoundSettings() }) }
        if (repo.cached() == null) requestLocation() else {
            PrayerScheduler.scheduleNext(this)
            refreshSchedule(false)
        }
    }

    private fun openSoundSettings() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val intent = android.content.Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, "prayer_times")
            }
            startActivity(intent)
        } else {
            startActivity(android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun createDailySync() {
        val request = PeriodicWorkRequestBuilder<PrayerWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daily_prayer_sync", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        else loadLocationAndRefresh()
    }

    @Suppress("MissingPermission")
    private fun loadLocationAndRefresh() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        var lat: Double? = null; var lon: Double? = null
        if (hasPermission) {
            val loc = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstNotNullOfOrNull { provider -> runCatching { if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider) else null }.getOrNull() }
            if (loc != null) { lat = loc.latitude; lon = loc.longitude; repo.saveLocation(loc.latitude, loc.longitude, "Lokasi GPS") }
        }
        refreshSchedule(true, lat, lon)
    }

    private fun refreshSchedule(fromGps: Boolean, lat: Double? = null, lon: Double? = null) {
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { repo.refresh(lat, lon, if (fromGps && lat != null) "Lokasi GPS" else null) } }
                .onSuccess { PrayerScheduler.scheduleNext(this@MainActivity); recreateContentRefresh() }
        }
    }

    private fun recreateContentRefresh() { setContent { SolatApp(repo, onRefresh = { refreshSchedule(false) }, onGps = { requestLocation() }, onSound = { openSoundSettings() }) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SolatApp(repo: PrayerRepository, onRefresh: () -> Unit, onGps: () -> Unit, onSound: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var times by remember { mutableStateOf(repo.cached()) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { times = repo.cached() }
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Gold, background = Cream, surface = Color.White)) {
        Scaffold(containerColor = Cream, bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.AccessTime, null) }, label = { Text("Jadwal") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.MenuBook, null) }, label = { Text("Bacaan") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Pengaturan") })
            }
        }) { padding ->
            when (tab) {
                0 -> HomeScreen(times, repo.locationLabel(), padding, onRefresh)
                1 -> ReadingsScreen(padding)
                else -> SettingsScreen(repo.locationLabel(), times?.fetchedAt ?: 0L, padding, onRefresh, onGps, onSound)
            }
        }
    }
}

@Composable
private fun HomeScreen(times: PrayerTimes?, location: String, padding: PaddingValues, onRefresh: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); now = System.currentTimeMillis() } }
    val upcoming = remember(times, now) { times?.entries()?.filter { it.first != "Terbit" }?.mapNotNull { (name, time) ->
        val parsed = runCatching { SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).parse("${times.date} $time")?.time }.getOrNull()
        parsed?.let { Triple(name, time, it) }
    }?.firstOrNull { it.third > now } }
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 22.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ASSALAMUALAIKUM", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("Waktu Solat", color = Forest, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(location, color = Color.Gray, fontSize = 13.sp)
                }
                Surface(shape = RoundedCornerShape(16.dp), color = Mint) { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Perbarui", tint = Green) } }
            }
        }
        item {
            Surface(color = Forest, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Text("WAKTU SOLAT BERIKUTNYA", color = Color(0xFFC7D9CF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(upcoming?.first ?: "Memuat jadwal", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(upcoming?.second ?: "--:--", color = Color(0xFFE7C98B), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Text(if (upcoming != null) countdown(upcoming.third - now) else "Tarik untuk memperbarui jadwal", color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
                }
            }
        }
        item { Text("JADWAL HARI INI", color = Forest, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp) }
        if (times == null) item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CloudDownload, null, tint = Green, modifier = Modifier.size(32.dp)); Spacer(Modifier.height(8.dp))
                Text("Jadwal belum tersedia", fontWeight = FontWeight.SemiBold); Text("Aktifkan internet lalu tekan tombol perbarui.", color = Color.Gray, fontSize = 13.sp)
            } }
        } else items(times.entries()) { (name, time) ->
            val isNext = upcoming?.first == name
            Surface(color = if (isNext) Mint else Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(if (isNext) Green else Cream, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(when (name) { "Subuh" -> Icons.Default.WbTwilight; "Zuhur" -> Icons.Default.WbSunny; "Asar" -> Icons.Default.LightMode; "Magrib" -> Icons.Default.WbTwilight; "Isya" -> Icons.Default.DarkMode; else -> Icons.Default.WbSunny }, null, tint = if (isNext) Color.White else Green)
                    }
                    Spacer(Modifier.width(14.dp)); Text(name, Modifier.weight(1f), color = Forest, fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium)
                    Text(time, color = Forest, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
        }
        item { Text("Metode perhitungan: Singapore (20° / 18°). Jadwal mengikuti koordinat perangkat.", color = Color.Gray, fontSize = 11.sp) }
    }
}

private fun countdown(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); val h = s / 3600; val m = (s % 3600) / 60
    return "Dalam ${h} jam ${m} menit"
}

@Composable
private fun ReadingsScreen(padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), contentPadding = PaddingValues(top = 22.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Bacaan Solat", color = Forest, fontSize = 27.sp, fontWeight = FontWeight.Bold); Text("Arab, latin, dan arti Bahasa Indonesia", color = Color.Gray, fontSize = 13.sp) }
        items(PrayerReadings.all) { reading ->
            var expanded by remember { mutableStateOf(false) }
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(reading.title, Modifier.weight(1f), color = Forest, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Buka bacaan", tint = Green) }
                    }
                    Text(reading.arabic, Modifier.fillMaxWidth(), color = Forest, fontSize = 24.sp, textAlign = TextAlign.End, lineHeight = 42.sp)
                    if (expanded) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Mint)
                        Text("LATIN", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(reading.latin, color = Color.DarkGray, fontSize = 14.sp, lineHeight = 22.sp)
                        Spacer(Modifier.height(10.dp)); Text("ARTI", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(reading.meaning, color = Color.DarkGray, fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }
            }
        }
        item { Text("Catatan: variasi lafaz doa iftitah dan bacaan sunah dapat berbeda menurut mazhab atau tuntunan yang diikuti.", color = Color.Gray, fontSize = 11.sp) }
    }
}

@Composable
private fun SettingsScreen(location: String, fetchedAt: Long, padding: PaddingValues, onRefresh: () -> Unit, onGps: () -> Unit, onSound: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), contentPadding = PaddingValues(top = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Pengaturan", color = Forest, fontSize = 27.sp, fontWeight = FontWeight.Bold); Text("Lokasi dan pembaruan jadwal", color = Color.Gray, fontSize = 13.sp) }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("LOKASI", color = Green, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp)); Text(location, color = Forest, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Spacer(Modifier.height(12.dp)); Button(onClick = onGps, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green)) { Icon(Icons.Default.MyLocation, null); Spacer(Modifier.width(8.dp)); Text("Gunakan lokasi GPS") }
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("SINKRONISASI", color = Green, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp)); Text("Jadwal diperbarui otomatis setiap 24 jam saat perangkat memiliki koneksi internet.", color = Color.DarkGray, fontSize = 14.sp, lineHeight = 21.sp)
                    Text(if (fetchedAt > 0) "Terakhir diperbarui: ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(fetchedAt))}" else "Belum pernah disinkronkan", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text("Sinkronkan sekarang") }
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("SUARA PENGINGAT", color = Green, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp)); Text("Atur nada notifikasi atau pilih audio adzan yang tersedia di perangkat melalui pengaturan kanal Android.", color = Color.DarkGray, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = onSound, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.NotificationsActive, null); Spacer(Modifier.width(8.dp)); Text("Atur suara adzan") }
                }
            }
        }
        item { Text("Mode offline: jadwal terakhir yang berhasil diunduh tetap disimpan di perangkat.", color = Color.Gray, fontSize = 12.sp, lineHeight = 18.sp) }
        item { Text("Versi 1.0.0 · Metode Singapore (20° / 18°) · Data: AlAdhan API", color = Color.Gray, fontSize = 11.sp) }
    }
}
