package com.forge.webview.bankapp;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WebViewForgeActivity";
    private WebView webView;
    private ValueCallback<Uri[]> mUploadMessage;
    private String mCameraPhotoPath;
    private SwipeRefreshLayout swipeRefresh;

    // Permissions Request Launcher
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            Log.d(TAG, "Permissions request callback received:");
            for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                Log.d(TAG, "Permission: " + entry.getKey() + " Granted: " + entry.getValue());
            }
        });

    // File Chooser Launcher
    private final ActivityResultLauncher<Intent> fileChooserLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Log.d(TAG, "File chooser activity returned with result code: " + result.getResultCode());
            if (mUploadMessage == null) return;
            
            Uri[] results = null;
            if (result.getResultCode() == RESULT_OK) {
                Intent data = result.getData();
                if (data != null) {
                    String dataString = data.getDataString();
                    ClipData clipData = data.getClipData();
                    if (clipData != null) {
                        results = new Uri[clipData.getItemCount()];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            results[i] = clipData.getItemAt(i).getUri();
                        }
                    } else if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
                
                // If no result from content selection, check camera capture
                if (results == null && mCameraPhotoPath != null) {
                    File file = new File(Uri.parse(mCameraPhotoPath).getPath());
                    if (file.exists() && file.length() > 0) {
                        results = new Uri[]{Uri.parse(mCameraPhotoPath)};
                    }
                }
            }
            
            mUploadMessage.onReceiveValue(results);
            mUploadMessage = null;
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_WebViewApp);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Initialize views
        webView = findViewById(R.id.webview);
        
        if (false) {
            swipeRefresh = findViewById(R.id.swipeRefresh);
            swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    Log.d(TAG, "Pull to Refresh triggered");
                    webView.reload();
                }
            });
        }

        // 2. Configure WebSettings
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setGeolocationEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        // Cookies and security
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // Hardware Acceleration
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);

        // 3. Configure WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page loaded successfully: " + url);
                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(TAG, "Navigating to URL: " + url);
                if (url == null) return false;

                // Handle external intent links (tel, mailto, whatsapp, etc)
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start external scheme intent: " + url, e);
                        return false;
                    }
                }

                // If "Abrir Links Externos" is enabled, load outside current webview if domains differ
                if (true) {
                    String currentHost = Uri.parse("https://geo-spot26.vercel.app").getHost();
                    String newHost = Uri.parse(url).getHost();
                    if (newHost != null && currentHost != null && !newHost.contains(currentHost) && !currentHost.contains(newHost)) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            startActivity(intent);
                            return true;
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to launch external browser for " + url, e);
                        }
                    }
                }

                return false;
            }
        });

        // 4. Configure WebChromeClient (FileChooser, Geolocation, Media Permission Request)
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                Log.d(TAG, "onGeolocationPermissionsShowPrompt called for: " + origin);
                // Automatically grant geolocation permission inside webview
                callback.invoke(origin, true, false);
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                Log.d(TAG, "onPermissionRequest called for resources: " + Arrays.toString(request.getResources()));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Grant requested resources (like camera and audio capture) dynamically
                        request.grant(request.getResources());
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                Log.d(TAG, "onShowFileChooser triggered");
                if (!true) {
                    Log.d(TAG, "File upload is not enabled in features. Denying file chooser request.");
                    return false;
                }

                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(null);
                }
                mUploadMessage = filePathCallback;

                Intent takePictureIntent = null;
                try {
                    takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                        File photoFile = null;
                        try {
                            photoFile = createCapturedFile();
                        } catch (IOException ex) {
                            Log.e(TAG, "Unable to create Image File", ex);
                        }
                        if (photoFile != null) {
                            mCameraPhotoPath = "file:" + photoFile.getAbsolutePath();
                            Uri photoURI = FileProvider.getUriForFile(MainActivity.this,
                                    getPackageName() + ".fileprovider",
                                    photoFile);
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                        } else {
                            takePictureIntent = null;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to configure camera intent in file chooser", e);
                    takePictureIntent = null;
                }

                Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                contentSelectionIntent.setType("*/*");
                if (fileChooserParams.getAcceptTypes() != null && fileChooserParams.getAcceptTypes().length > 0) {
                    String primaryType = fileChooserParams.getAcceptTypes()[0];
                    if (primaryType != null && !primaryType.trim().isEmpty()) {
                        contentSelectionIntent.setType(primaryType);
                    }
                }

                if (fileChooserParams.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                    contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }

                Intent[] intentArray;
                if (takePictureIntent != null) {
                    intentArray = new Intent[]{takePictureIntent};
                } else {
                    intentArray = new Intent[0];
                }

                Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "Selecionar Arquivo");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);

                try {
                    fileChooserLauncher.launch(chooserIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch file chooser dialog activity", e);
                    if (mUploadMessage != null) {
                        mUploadMessage.onReceiveValue(null);
                        mUploadMessage = null;
                    }
                    return false;
                }

                return true;
            }
        });

        // 5. Configure DownloadListener
        if (true) {
            webView.setDownloadListener(new DownloadListener() {
                @Override
                public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                    Log.d(TAG, "onDownloadStart triggered for URL: " + url);
                    try {
                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                        request.setMimeType(mimeType);
                        String cookies = CookieManager.getInstance().getCookie(url);
                        request.addRequestHeader("cookie", cookies);
                        request.addRequestHeader("User-Agent", userAgent);
                        request.setDescription("Baixando arquivo...");
                        String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
                        request.setTitle(filename);
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                        
                        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        if (dm != null) {
                            dm.enqueue(request);
                            Toast.makeText(getApplicationContext(), "Download iniciado: " + filename, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getApplicationContext(), "Erro: DownloadManager indisponível", Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to launch DownloadManager", e);
                        Toast.makeText(getApplicationContext(), "Erro ao iniciar download", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        // 6. Request Runtime Permissions
        List<String> listPermissions = new ArrayList<>();
        listPermissions.add("android.permission.READ_EXTERNAL_STORAGE");
        listPermissions.add("android.permission.READ_MEDIA_IMAGES");
        listPermissions.add("android.permission.READ_MEDIA_VIDEO");
        listPermissions.add("android.permission.ACCESS_FINE_LOCATION");
        listPermissions.add("android.permission.ACCESS_COARSE_LOCATION");
        listPermissions.add("android.permission.WRITE_EXTERNAL_STORAGE");
        listPermissions.add("android.permission.VIBRATE");
        listPermissions.add("android.permission.CALL_PHONE");
        listPermissions.add("android.permission.POST_NOTIFICATIONS");

        if (!listPermissions.isEmpty()) {
            Log.d(TAG, "Requesting permissions dynamically: " + listPermissions);
            requestPermissionsLauncher.launch(listPermissions.toArray(new String[0]));
        }

        // 7. Load WebView
        webView.loadUrl("https://geo-spot26.vercel.app");
    }

    private File createCapturedFile() throws IOException {
        String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
    }

    @Override
    public void onBackPressed() {
        if (true && webView != null && webView.canGoBack()) {
            Log.d(TAG, "Smart back button navigation executed");
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
