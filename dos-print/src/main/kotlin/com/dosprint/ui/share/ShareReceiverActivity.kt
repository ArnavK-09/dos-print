package com.dosprint.ui.share

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

@Suppress("DEPRECATION")
class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, com.dosprint.ui.MainActivity::class.java)
        extractUris(this.intent).firstOrNull()?.let(intent::setData)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    private fun extractUris(source: Intent?): List<Uri> {
        if (source == null) {
            return emptyList()
        }
        val uris = LinkedHashSet<Uri>()
        source.data?.let(uris::add)
        source.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.forEach(uris::add)
        if (source.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            source.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(uris::add)
        }
        return uris.toList()
    }
}
