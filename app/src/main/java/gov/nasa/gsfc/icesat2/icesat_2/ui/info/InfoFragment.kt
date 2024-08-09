package gov.nasa.gsfc.icesat2.icesat_2.ui.info

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.youtube.player.YouTubeStandalonePlayer
import gov.nasa.gsfc.icesat2.icesat_2.R
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

// import kotlinx.android.synthetic.main.fragment_info.* // DEPRECATED LANGUAGE

class InfoFragment : Fragment() {
    private lateinit var textViewDateRange: TextView
    private lateinit var textViewWatchVideo: TextView
    private lateinit var textViewInfo1: TextView
    private lateinit var textViewInfo2: TextView
    private lateinit var textViewInfo3: TextView
    private lateinit var textViewInfo4: TextView
    private lateinit var webViewYouTube: WebView

    companion object {
        private const val TAG = "InfoFragment"
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "START/END onCreateView - InfoFragment")
        return inflater.inflate(R.layout.fragment_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "START onViewCreated - InfoFragment")
        super.onViewCreated(view, savedInstanceState)
        textViewDateRange = view.findViewById(R.id.textViewDateRange)
        textViewWatchVideo = view.findViewById(R.id.textViewWatchVideo)
        textViewInfo1 = view.findViewById(R.id.textViewInfo1)
        textViewInfo2 = view.findViewById(R.id.textViewInfo2)
        textViewInfo3 = view.findViewById(R.id.textViewInfo3)
        textViewInfo4 = view.findViewById(R.id.textViewInfo4)
        webViewYouTube = view.findViewById(R.id.webViewYouTube)

        textViewDateRange.text = getString(R.string.currentData, "Date 1, 2024 - Date 2, 2024")


        // Configure WebView
        val webSettings: WebSettings = webViewYouTube.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webViewYouTube.webViewClient = WebViewClient()
        // Initially hide the WebView
        webViewYouTube.visibility = View.GONE
        // Handle click event on textViewWatchVideo
        textViewWatchVideo.setOnClickListener {
            // Show the WebView
            webViewYouTube.visibility = View.VISIBLE

            // Load the YouTube video in the WebView
            val videoId = "OQg5ov6zths"
            val videoUrl = "https://www.youtube.com/embed/$videoId"
            webViewYouTube.loadUrl(videoUrl)
        }

        // Clicking on links takes you to the appropriate webpage
        textViewInfo1.movementMethod = LinkMovementMethod.getInstance()
        textViewInfo2.movementMethod = LinkMovementMethod.getInstance()
        textViewInfo3.movementMethod = LinkMovementMethod.getInstance()
        textViewInfo4.movementMethod = LinkMovementMethod.getInstance()
        Log.d(TAG, "END onViewCreated - InfoFragment")
    }
}