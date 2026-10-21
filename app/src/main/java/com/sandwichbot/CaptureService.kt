package com.sandwichbot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        startFg()
        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, Handler(Looper.getMainLooper()))
        startCapture()
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val b: Rect = if (Build.VERSION.SDK_INT >= 30) {
            wm.maximumWindowMetrics.bounds
        } else {
            val p = Point()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(p)
            Rect(0, 0, p.x, p.y)
        }
        val fw = b.width(); val fh = b.height()
        val cw = fw / 4; val ch = fh / 4
        val tracker = Tracker(cw, ch)

        val t = HandlerThread("capture").also { it.start() }
        thread = t
        val rd = ImageReader.newInstance(cw, ch, PixelFormat.RGBA_8888, 2)
        reader = rd
        display = projection!!.createVirtualDisplay(
            "sandwich", cw, ch, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, rd.surface, null, null
        )

        var taps = 0
        rd.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val pl = img.planes[0]
                val buf = pl.buffer
                val rs = pl.rowStride
                val ps = pl.pixelStride
                val out = tracker.buffer()
                for (y in 0 until ch) {
                    var o = y * rs
                    val row = y * cw
                    for (x in 0 until cw) {
                        out[row + x] = ((buf.get(o).toInt() and 0xFF) +
                                (buf.get(o + 1).toInt() and 0xFF) +
                                (buf.get(o + 2).toInt() and 0xFF)) / 3
                        o += ps
                    }
                }
                if (tracker.process(SystemClock.elapsedRealtime())) {
                    TapService.instance?.tap(fw * 0.5f, fh * 0.5f)
                    taps++
                    Log.d("SandwichBot", "TAP #$taps")
                }
            } finally {
                img.close()
            }
        }, Handler(t.looper))
    }

    private fun startFg() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("sb", "Sandwich Bot", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "sb")
            .setContentTitle("Sandwich Bot chal raha hai")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }

    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.stop()
        thread?.quitSafely()
        super.onDestroy()
    }
}

