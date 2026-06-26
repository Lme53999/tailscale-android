// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.tailscale.ipn.util.BrowserOpener
import com.tailscale.ipn.util.InlineShare
import com.tailscale.ipn.util.TSLog

// Handles taps on Taildrop inline-share notifications: URL → browser, text → clipboard.
class InlineShareActionReceiver : BroadcastReceiver() {
  companion object {
    private const val TAG = "InlineShareActionReceiver"
    const val ACTION_CONSUME = "com.tailscale.ipn.INLINE_SHARE_CONSUME"
    const val EXTRA_KIND = "kind"
    const val EXTRA_CONTENT = "content"
    const val EXTRA_ID = "id"
  }

  override fun onReceive(context: Context, intent: Intent) {
    val kindRaw = intent.getStringExtra(EXTRA_KIND) ?: return
    val content = intent.getStringExtra(EXTRA_CONTENT) ?: return
    val id = intent.getStringExtra(EXTRA_ID)

    val kind =
        runCatching { InlineShare.Kind.valueOf(kindRaw.uppercase()) }.getOrNull()
            ?: run {
              TSLog.w(TAG, "unknown inline share kind: $kindRaw")
              return
            }

    when (kind) {
      InlineShare.Kind.URL -> openUrl(context, content)
      InlineShare.Kind.TEXT -> copyToClipboard(context, content)
    }

    if (id != null) {
      com.tailscale.ipn.ui.notifier.Notifier.removeInlineShare(id)
    }
  }

  private fun openUrl(context: Context, content: String) {
    val uri = runCatching { Uri.parse(content) }.getOrNull()?.takeIf { !it.scheme.isNullOrEmpty() }
    if (uri == null) {
      copyToClipboard(context, content)
      return
    }
    if (!BrowserOpener.openInDefaultBrowser(context, uri)) {
      TSLog.w(TAG, "failed to open URL $content")
      copyToClipboard(context, content)
    }
  }

  private fun copyToClipboard(context: Context, content: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Tailscale", content))
    Toast.makeText(context, R.string.taildrop_copied_to_clipboard, Toast.LENGTH_SHORT).show()
  }
}
