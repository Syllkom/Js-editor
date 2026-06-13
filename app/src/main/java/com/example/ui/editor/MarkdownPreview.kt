package com.example.ui.editor

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun MarkdownPreview(markdownText: String, isDark: Boolean) {
    val bgHex = if (isDark) "#1E1E1E" else "#FFFFFF"
    val textHex = if (isDark) "#D4D4D4" else "#000000"
    
    // Very simple inline md-to-html conversion just for preview.
    val html = """
        <html>
        <head>
            <style>
                body {
                    background-color: $bgHex;
                    color: $textHex;
                    font-family: sans-serif;
                    padding: 16px;
                    line-height: 1.6;
                }
                pre, code {
                    background-color: ${if (isDark) "#2D2D2D" else "#F5F5F5"};
                    padding: 4px;
                    border-radius: 4px;
                }
                pre { padding: 12px; overflow-x: auto; }
                a { color: #569CD6; }
            </style>
            <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
        </head>
        <body>
            <div id="content"></div>
            <script>
                document.getElementById('content').innerHTML = marked.parse(decodeURIComponent("${java.net.URLEncoder.encode(markdownText, "UTF-8").replace("+", "%20")}"));
            </script>
        </body>
        </html>
    """.trimIndent()
    
    AndroidView(
        factory = { ctx -> 
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = Modifier.fillMaxSize()
    )
}
