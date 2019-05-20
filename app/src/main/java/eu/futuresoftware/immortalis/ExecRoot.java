package eu.futuresoftware.immortalis;

/*
 * Created by Boudewijn van Breukelen at 2018-11-20
 * This class centralises all usage of command-line executions as root.
 * Multiple commands can be provided.
 * The class can be used in two ways:
 * ExecRoot.withoutResponse executes a command as root and it will not be waited for, nor will it return anything.
 *   Example:
        ExecRoot.withoutResponse(new String[]{ "ls", "-l" });

 * ExecRoot.waitWithoutResponse executes a command as root and waits for termination on the same thread.
 *   Returns true or false depending on success of executed command.
 *   DO NOT RUN THIS FROM THE UI THREAD. Use WithResponse on the UI thread to avoid locking up the thread.
 *
 * ExecRoot.WithResponse executes a command as root and will return the provided callback when done.
 *   This returns null on error or the output on success.
 *   This uses an asynctask so won't lock up the UI
 *   It will kill the process after 5 seconds to make sure it doesn't hang.
 *   Note that commands are queued and will not execute in parallel.
 *   Example:
        new ExecRoot.WithResponse("ls", new ExecRoot.OnExecEventListener<String>() {
            @Override
            public void onCompleted(String result) {
                if (result != null) {
                    Log.e("ExecRoot", "Success");
                    Log.e("ExecRoot", result);
                } else {
                    Log.e("ExecRoot", "Failed");
                }
            }
        }).execute();

 * null will be returned if the execution fails, if it times out or if the process exists with and exitcode > 0
 * If multiple commands are provided, the exit code of the last will be used.
 */

import android.os.AsyncTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeoutException;

public class ExecRoot {

    public interface OnExecEventListener<String> {
        public void onCompleted(String output);
    }

    public static class WithResponse extends AsyncTask<Void, Void, String> {

        private ExecRoot.OnExecEventListener<String> mCallBack;
        private String mCommand;

        public WithResponse(String cmd, ExecRoot.OnExecEventListener<String> callback) {
            mCallBack = callback;
            mCommand = cmd;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                Process su = Runtime.getRuntime().exec(new String[] {"su", "-c", mCommand});
                InputStream inputStream = su.getInputStream();

                // Add timeout
                Worker worker = new Worker(su);
                worker.start();

                try {
                    worker.join(5000);

                    if (worker.mExit != null) {
                        String output = readFully(inputStream);

                        if (worker.mExit == 0) {
                            return output;
                        }
                    } else {
                        throw new TimeoutException();
                    }
                } catch(InterruptedException ex) {
                    worker.interrupt();
                    Thread.currentThread().interrupt();

                    throw ex;
                } finally {
                    inputStream.close();
                    su.destroy();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (mCallBack != null) { mCallBack.onCompleted(result); }
        }
    }

    public static void withoutResponse(final String command) {
        try {
            Runtime.getRuntime().exec(new String[] {"su", "-c", command});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean waitWithoutResponse(final String command) {
        try {
            Process su = Runtime.getRuntime().exec(new String[] {"su", "-c", command});

            return su.waitFor() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static class Worker extends Thread {
        private final Process mProcess;
        private Integer mExit;

        private Worker(Process process) {
            this.mProcess = process;
        }

        public void run() {
            try {
                mExit = mProcess.waitFor();
            } catch (InterruptedException ignore) { }
        }
    }

    private static String readFully(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;

        while ((length = is.read(buffer)) != -1) {
            baos.write(buffer, 0, length);
        }

        return baos.toString("UTF-8");
    }
}