package jakobkarolus.de.pulseradar;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class PulseRadar extends ActionBarActivity {

    private static final int SAMPLE_RATE = 44100;
    private static final double STD_FREQ = 20000;
    private DataOutputStream dos;
    private File tempFile;

    private AudioTrack at;
    private AudioRecord ar;

    private boolean recordRunning = false;
    private double currentFreq;

    private static final int minSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pulse_radar);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
        at = new AudioTrack(AudioManager.STREAM_MUSIC,SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,minSize,AudioTrack.MODE_STREAM);
        ar = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 10* minSize);

        currentFreq = STD_FREQ;

    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    /**
     * called when pressing the "Start recording" button.<br>
     * Starts the record and play threads
     *
     * @param view
     * @throws FileNotFoundException
     */
    public void startRecord(View view) throws FileNotFoundException {

        tempFile = new File(PulseRadar.this.getExternalCacheDir().getAbsolutePath() + "/temp.raw");

        if(tempFile.exists())
            tempFile.delete();

        dos = new DataOutputStream(new FileOutputStream(tempFile));
        ar.startRecording();
        recordRunning = true;
        Thread recordThread = new Thread(new Runnable() {
            @Override
            public void run() {

                short[] buffer = new short[minSize];
                while(recordRunning){
                    ar.read(buffer, 0, minSize);
                    try {
                        ByteBuffer bytes = ByteBuffer.allocate(buffer.length * 2);
                        for (short s : buffer) {
                            bytes.putShort(s);
                        }
                        dos.write(bytes.array());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        });
        recordThread.start();


        at.play();

        Thread playThread = new Thread(new Runnable() {
            @Override
            public void run() {

                while(recordRunning){
                    byte[] audio = generateAudio(currentFreq, 0.1, 1.0);
                    at.flush();
                    at.write(audio, 0, audio.length);
                }
            }
        });
        playThread.start();
    }

    /**
     * called when pressing the "Stop recording" button.<br>
     * Finishes play and record thread and ask for filename.<br>
     * Saved files can be found in the app-own directory (Android/data/...)
     *
     * @param view
     * @throws IOException
     */
    public void stopRecord(View view) throws IOException {

        recordRunning = false;
        ar.stop();
        dos.flush();
        dos.close();

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        AskForFileNameDialog fileNameDialog = new AskForFileNameDialog();
        fileNameDialog.show(ft, "FileNameDialog");
    }


    @SuppressLint("ValidFragment")
    public class AskForFileNameDialog extends DialogFragment{

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            LayoutInflater inflater = getActivity().getLayoutInflater();
            final View view = inflater.inflate(R.layout.dialog_record_name,	null);
            final EditText fileName = (EditText) view.findViewById(R.id.input_filename_record);
            fileName.setText("test");
            builder.setView(view);
            builder.setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {

                            //EditText fileName = (EditText) view
                              //      .findViewById(R.id.input_filename_record);

                            try {
                                saveWaveFile(fileName.getText().toString());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        }
                    });
            builder.setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            AskForFileNameDialog.this.getDialog().cancel();
                        }
                    });

            return builder.create();
        }
    }


    private void saveWaveFile(String waveFileName) throws IOException {

        int dataLength = (int) tempFile.length();
        byte[] rawData = new byte[dataLength];

        DataInputStream input = null;
        try {
            input = new DataInputStream(new FileInputStream(tempFile));
            input.read(rawData);
        } finally {
            if (input != null) {
                input.close();
            }
        }

        DataOutputStream output = null;
        try {
            output = new DataOutputStream(new FileOutputStream(new File(PulseRadar.this.getExternalCacheDir().getAbsolutePath() + "/" + waveFileName + ".wav")));
            // WAVE header
            // see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
            writeString(output, "RIFF"); // chunk id
            writeInt(output, 36 + dataLength); // chunk size
            writeString(output, "WAVE"); // format
            writeString(output, "fmt "); // subchunk 1 id
            writeInt(output, 16); // subchunk 1 size
            writeShort(output, (short) 1); // audio format (1 = PCM)
            writeShort(output, (short) 1); // number of channels
            writeInt(output, SAMPLE_RATE); // sample rate
            writeInt(output, SAMPLE_RATE * 2); // byte rate
            writeShort(output, (short) 2); // block align
            writeShort(output, (short) 16); // bits per sample
            writeString(output, "data"); // subchunk 2 id
            writeInt(output, dataLength); // subchunk 2 size
            // Audio data (conversion big endian -> little endian)
            short[] shorts = new short[rawData.length / 2];
            ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
            ByteBuffer bytes = ByteBuffer.allocate(shorts.length * 2);
            for (short s : shorts) {
                bytes.putShort(s);
            }
            output.write(bytes.array());
        }

        finally {
            if (output != null) {
                output.close();
            }
        }
    }

    /**
     * causes the play thread to generate a signal of a specific frequency for 500ms to discern events
     * @param view
     */
    public void playSignal(View view){

        currentFreq = 19000.0;

        CountDownTimer timer = new CountDownTimer(500, 500) {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                currentFreq = STD_FREQ;
            }
        };
        timer.start();

    }

    private byte[] generateAudio(double frequency, double seconds, double amplitude){

        float[] buffer = new float[(int) (seconds * SAMPLE_RATE)];


        for (int sample = 0; sample < buffer.length; sample++) {
            double time = sample / (double) SAMPLE_RATE;

            //if(sample == buffer.length/2)
            //  buffer[sample] = 1.0f;
            buffer[sample] = (float) (amplitude * Math.sin(frequency*2.0*Math.PI * time));
        }

        final byte[] byteBuffer = new byte[buffer.length * 2];
        int bufferIndex = 0;
        for (int i = 0; i < byteBuffer.length; i++) {
            final int x = (int) (buffer[bufferIndex++] * 32767.0);
            byteBuffer[i] = (byte) x;
            i++;
            byteBuffer[i] = (byte) (x >>> 8);
        }

        return byteBuffer;
    }

    private void writeInt(final DataOutputStream output, final int value) throws IOException {
        output.write(value >> 0);
        output.write(value >> 8);
        output.write(value >> 16);
        output.write(value >> 24);
    }

    private void writeShort(final DataOutputStream output, final short value) throws IOException {
        output.write(value >> 0);
        output.write(value >> 8);
    }

    private void writeString(final DataOutputStream output, final String value) throws IOException {
        for (int i = 0; i < value.length(); i++) {
            output.write(value.charAt(i));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_pulse_radar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_pulse_radar, container, false);
            return rootView;
        }
    }
}