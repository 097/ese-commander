package com.miui.ese.commander;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ApduExecutorActivity extends Activity {

    private static final String TAG = "ApduExecutorActivity";

    private static final String SCAN_PATH = Environment.getExternalStorageDirectory().getPath() + "/apdu/";

    private static final String APDU_SUFFIX = ".apdu";

    private ScanAPDUFileTask mAsyncTask;

    private AsyncTask mExecuteESETask;

    private TextView mMsgView;

    private LinearLayout mPanelView;

    private LayoutInflater mInflater;

    private INfcSecureElement mSE;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mInflater = LayoutInflater.from(this);

        mMsgView = (TextView) findViewById(R.id.msg);
        mPanelView = (LinearLayout) findViewById(R.id.panel);

        mAsyncTask = new ScanAPDUFileTask();
        mAsyncTask.execute(SCAN_PATH);

        // initialize with DummyNfcSecureElement if you want to test
//        mSE = new DummyNfcSecureElement();

        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        mSE = new NfcSecureElement(nfcAdapter);
    }

    @Override
    protected void onDestroy() {
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
        }
        super.onDestroy();
    }

    private class ScanAPDUFileTask extends AsyncTask<String, Void, List<APDUFileContent>> {

        @Override
        protected List<APDUFileContent> doInBackground(String... params) {
            String rootPath = params[0];
            ArrayList<APDUFileContent> result = new ArrayList<APDUFileContent>();
            File rootFile = new File(rootPath);
            if (rootFile.exists() && rootFile.isDirectory()) {
                Log.i(TAG, "scan apdu script in dir: " + rootFile.getAbsolutePath());
                File[] files = rootFile.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        Log.i(TAG, "accepting : " + filename);
                        return filename.endsWith(APDU_SUFFIX);
                    }
                });

                Log.i(TAG, "find " + (files != null ? files.length : 0) + " files");
                if (files != null) {
                    for (File f : files) {
                        APDUFileContent apduFile = new APDUFileContent();
                        apduFile.name = f.getName();
                        apduFile.filePath = f;

                        result.add(apduFile);
                    }
                }
            } else {
                Log.w(TAG, "not scan apdu script in dir, dir not exist: " + rootFile.getAbsolutePath());
            }
            return result;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mMsgView.setText(getString(R.string.msg_start_scan, SCAN_PATH));
        }

        @Override
        protected void onPostExecute(List<APDUFileContent> apduFileContents) {
            super.onPostExecute(apduFileContents);
            mMsgView.setText(getString(R.string.msg_finish_scan, apduFileContents.size()));

            mPanelView.removeAllViews();

            for (final APDUFileContent apduFile : apduFileContents) {
                Button button = (Button) mInflater.inflate(R.layout.apdu_button_layout, mPanelView, false);
                button.setText(apduFile.name);
                mPanelView.addView(button);

                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        v.setTag(apduFile);

                        if (mExecuteESETask != null) {
                            mExecuteESETask.cancel(true);
                        }
                        mExecuteESETask = new ExecuteESETask().execute(apduFile);
                    }
                });
            }

        }
    }

    private class ExecuteESETask extends AsyncTask<APDUFileContent, Void, ExecuteApduResult> {

        private String taskName;

        @Override
        protected ExecuteApduResult doInBackground(APDUFileContent... params) {
            File file = params[0].filePath;
            taskName = params[0].name;

            if (!file.isDirectory() && file.exists()) {
                List<APDU> apdus = getApdus(file);
                Log.d(TAG, "get " + apdus.size() + " apdu(s) from file: " + file.getAbsolutePath());

                // execute
                return executeEse(apdus);
            } else {
                Log.w(TAG, "file not exists: " + file.getAbsolutePath());
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mPanelView.setEnabled(false);
        }

        @Override
        protected void onPostExecute(ExecuteApduResult executeApduResult) {
            super.onPostExecute(executeApduResult);
            mPanelView.setEnabled(true);
            if (executeApduResult.successCount == executeApduResult.totalCount) {
                mMsgView.setText(getString(R.string.execution_finish, taskName, executeApduResult.totalCount, executeApduResult.successCount, new Date().toString()));
            } else {
                if (executeApduResult.lastSw == null) {
                    executeApduResult.lastSw = "null";
                }
                mMsgView.setText(getString(R.string.execution_finish_with_error, taskName, executeApduResult.totalCount, executeApduResult.successCount, executeApduResult.lastSw, new Date().toString()));
            }
        }

        @Override
        protected void onCancelled(ExecuteApduResult executeApduResult) {
            super.onCancelled(executeApduResult);
            mPanelView.setEnabled(true);
            mMsgView.setText(R.string.execution_canceled);
        }
    }

    private ExecuteApduResult executeEse(List<APDU> apdus) {
        ExecuteApduResult result = new ExecuteApduResult();

        mSE.openSecureElementConnection();

        result.totalCount = apdus.size();
        try {
            String lastSw = null;
            for (APDU apdu : apdus) {
                if (Thread.currentThread().isInterrupted()) {
                    Log.w(TAG, "thread is interrupted, skip executing ese command");
                    break;
                }
                try {
                    byte[] apduBytes = HexDump.hexStringToByteArray(apdu.apdu);
                    byte[] response = mSE.exchangeAPDU(apduBytes);
                    if (response != null && response.length >= 2) {
                        String sw = HexDump.toHexString(response, response.length - 2, 2).toUpperCase();
                        if (apdu.expectedStatus.contains(sw)) {
                            ++result.successCount;
                        } else {
                            Log.w(TAG, "unexpected status for command: " + apdu.apdu + ", sw: " + sw);
                        }
                        lastSw = sw;
                    } else {
                        Log.e(TAG, "invalid response from se, apdu: " + apdu.apdu);
                    }
                    result.lastApdu = apdu.apdu;
                    result.lastSw = lastSw;
                    lastSw = null;
                } catch (Exception e) {
                    Log.e(TAG, "exception", e);
                }
            }
        } finally {
            mSE.closeSecureElementConnection();
        }

        return result;
    }

    private List<APDU> getApdus(File file) {
        ArrayList<APDU> apduList = new ArrayList<APDU>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String line;
            do {
                line = br.readLine();
                if (line != null) {
                    apduList.add(APDU.parse(line));
                }
            } while (line != null && !Thread.currentThread().isInterrupted());
        } catch (FileNotFoundException fe) {
            Log.e(TAG, "file not found", fe);
        } catch (IOException e) {
            Log.e(TAG, "io exception", e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return apduList;
    }


    static class APDUFileContent {
        String name;
        File filePath;
    }

    static class ExecuteApduResult {
        int totalCount;
        int successCount;

        String lastApdu;
        String lastSw;
    }
}
