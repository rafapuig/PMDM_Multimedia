package es.rafapuig.pmdm.multimedia.playerrecorder.sound;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;


import es.rafapuig.pmdm.multimedia.playerrecorder.sound.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    // States in which the player may be.
    // Helps to know which operations can or cannot be performed at any given time.
    private enum PlayerState {STOPPED, PLAYING, PAUSED, RECORDING}

    PlayerState currentState = PlayerState.STOPPED; // At the beginning the player is stopped

    // Number of milliseconds that playback will skip forward or backward when pressing << or >>.
    private final int DELTA_TIME_MS = 5000; // in ms = 5 secs

    // Name of the file where the recording will be stored
    private final String RECORDING_FILENAME = "MyRecording.3gp";

    // Playback manager (plays the selected song in the spinner)
    private MediaPlayer mediaPlayer;
    private MediaRecorder mediaRecorder; // Recording manager

    //It updates while the song is playing to know where to resume playback after a pause
    private int playbackPosition = 0;

    // Path where "MyRecording.3gp" will be saved on the external storage
    private String recordingPath;


    // Names of the songs in the Spinner
    private final String[] songNames = {"Scumm Bar song", "Mercuria", "Last Recording"};

    // Corresponding resources of the songs in the raw folder
    // (the last recording will be in the external file storage system)
    private final int[] songIds = {R.raw.thescummbar, R.raw.mercuria};

    // Used to graphically update the time counter / progressbar.
    private Runnable timer;
    private static final int REFRESH_FREQUENCY_TIME_IN_MILLIS = 100;

    ActivityMainBinding binding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupPermissions();
        setupExternalStorageForRecording();
        //MediaStore.createWriteRequest()
        setupSongsSpinner();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //TODO: If when the focus returns to the activity it was playing, continue playing where it left off.
        if (currentState == PlayerState.PLAYING) {
            resumePlayback();
        }
    }

    @Override
    protected void onPause() {
        //TODO: Pause MediaPlayer playback
        if (mediaPlayer != null) mediaPlayer.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // TODO: Release playback and recording managers
        if (mediaPlayer != null) mediaPlayer.release();
        if (mediaRecorder != null) mediaRecorder.release();
        super.onDestroy();
    }


    private static final String TAG = "SoundPlayer";

    private static final int RECORD_AUDIO_REQUEST_CODE = 100;

    private void setupPermissions() {
        setupRecordAudioPermission();
    }

    private void setupRecordAudioPermission() {
        Log.i(TAG, "Try requesting record audio permission...");

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) {

            requestRecordAudio();
        }
    }

    private void requestRecordAudio() {
        if (shouldShowRequestPermissionRationale(
                Manifest.permission.RECORD_AUDIO)) {
            showPermissionRequestDialog();
        } else {
            makeRequestRecordAudio();
        }
    }

    private void showPermissionRequestDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permission Required");
        builder.setMessage("Permission is required to access the microphone and record the voice.");
        builder.setPositiveButton("OK",
                (dialog, which) -> makeRequestRecordAudio());

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void makeRequestRecordAudio() {
        Log.i(TAG, "Requesting record audio permission...");
        requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},
                RECORD_AUDIO_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permission Denied.");
            } else {
                Log.i(TAG, "Permission granted");
            }
        }
    }

    private void setupExternalStorageForRecording() {
        // Initialize the path to the recording file (String)
        File recordingDir = this.getExternalFilesDir(Environment.DIRECTORY_MUSIC); // .DIRECTORY_RECORDINGS);

        recordingPath =
                recordingDir.getAbsolutePath() +
                        File.separator + RECORDING_FILENAME;
    }

    private void setupSongsSpinner() {
        binding.songsSpinner.setAdapter(
                new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, songNames));
    }


    private void setupListeners() {
        setupSpinnerListener();
        setupVolumeSeekBarListener();
        binding.playImageButton.setOnClickListener(view -> onPlayButtonClick());
        binding.pauseImageButton.setOnClickListener(view -> onPauseButtonClick());
        binding.rewindImageButton.setOnClickListener(view -> onRewindButtonClick());
        binding.fastForwardImageButton.setOnClickListener(view -> onFastForwardClick());
        binding.recordImageButton.setOnClickListener(view -> onRecordButtonClick());
    }



    /**
     * The fast forward button: will only operate if playing back
     */
    private void onFastForwardClick() {
        // Remember that you have the current state in the attribute "currentState".
        //TODO: Position the DELTA_TIME_MS playback manager millisecond forward if in playing state.
        if (currentState == PlayerState.PLAYING) {
            mediaPlayer.seekTo(playbackPosition + DELTA_TIME_MS);
        }
    }

    /**
     * The rewind button: will only work if playing back
     */
    private void onRewindButtonClick() {
        //Remember that you have the current state in the attribute "currentState".
        //TODO: Position the DELTA_TIME_MS playback manager milliseconds further back if in playing state.
        if (currentState == PlayerState.PLAYING) {
            mediaPlayer.seekTo(playbackPosition - DELTA_TIME_MS);
        }
    }


    /**
     * Rec button: If it was recording, it will stop. If not, it will start recording.
     * In any case, if you were playing back, you should pause it.
     */
    private void onRecordButtonClick() {
        //Remember that you have the current status in the attribute "currentState"
        // TODO: If it was playing it should be paused (in any case)
        if (currentState == PlayerState.PLAYING)
            pausePlayback();

        //TODO: If it was recording, it will stop. If not, it will start recording.
        if (currentState == PlayerState.RECORDING) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    /**
     * Starts recording in the MediaRecorder
     */
    private void startRecording() {
        // You must indicate that you want to record from the microphone
        // with 3GP format and AMR_NB encoder.
        try {
            // TODO: Check if you have permission to record audio
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                //TODO: instantiate a MediaRecorder
                mediaRecorder = new MediaRecorder(); //new MediaRecorder(this);
                //TODO: set microphone as audio source
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                //TODO: set 3GPP as output format
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                //TODO: set the audio encoder to AMR_NB
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                //TODO: set the output file
                mediaRecorder.setOutputFile(recordingPath);
                //TODO: prepare the MediaRecorder
                mediaRecorder.prepare();
                //TODO: start capturing audio
                mediaRecorder.start();

                //TODO: Change player activity state to recording
                changePlayerState(PlayerState.RECORDING);
                binding.recordImageButton.setHovered(true);
            } else {
                //TODO: request permissions to record audio
                requestRecordAudio();
            }
        } catch (IOException e) {
            Toast.makeText(this,
                            "Input/output error: " + e.getMessage(), Toast.LENGTH_LONG)
                    .show();
        }
    }

    /**
     * Stops recording (Media Recorder) and automatically starts playing back the recording
     */
    private void stopRecording() {
        //TODO: Stop the recording manager and free up resources
        mediaRecorder.stop();
        mediaRecorder.release();

        binding.recordImageButton.setHovered(false);

        changePlayerState(PlayerState.PLAYING);

        // Start playback of recorded material
        binding.songsSpinner.setSelection(binding.songsSpinner.getCount() - 1);
        playRecording();
    }


    private void setupSpinnerListener() {
        // Spinner with songs:
        binding.songsSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        selectAudioTrack();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });
    }

    /**
     * Must differentiate between existing song selection or the last recording
     */
    private void selectAudioTrack() {
        Log.i(TAG, "Selecting audio track...");
        if (binding.songsSpinner.getSelectedItemPosition()
                == songNames.length - 1) {
            playRecording();
        } else {
            playSong();
        }
    }

    /**
     * Manages the song change (MediaPlayer).
     * If it was playing, it should start playing the new track (from the beginning).
     * The previous MediaPlayer must be released!
     */
    private void playSong() {
        //TODO: Release the previous mediaPlayer (if there is one)
        if (mediaPlayer != null) mediaPlayer.release();

        // TODO: get the index of the song from spinner position index
        int index = binding.songsSpinner.getSelectedItemPosition();

        // TODO: Here you have to indicate a resource (song) to the manager (play)
        MediaPlayer player = MediaPlayer.create(
                this.getApplicationContext(),
                songIds[index]);

        setMediaPlayer(player);

        //TODO: if it was playing a song, start the playback
        if (currentState == PlayerState.PLAYING)
            startPlayback();
    }

    /**
     * Manages the music change (playback manager)
     * If it was playing, it should start the playback of the recorded track.
     * Here you have to indicate to the manager a URI corresponding to the recording file.
     * The previous manager must be released!
     */
    private void playRecording() {

        File recodingFile = new File(recordingPath);
        if (recodingFile.exists()) {
            Uri recordingURI = Uri.fromFile(recodingFile);

            //TODO: Create MediaPlayer instance to play the recorded audio track
            MediaPlayer recordingPlayer =
                    MediaPlayer.create(this, recordingURI);

            //TODO: release the resources of the previous mediaPlayer (if any)
            if (mediaPlayer != null) mediaPlayer.release();

            setMediaPlayer(recordingPlayer);

            if (currentState == PlayerState.PLAYING) {
                startPlayback();
            }
        } else {
            // It should warn you if no recording file has been created yet!
            Toast.makeText(this,
                            "There is no recording", Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void setMediaPlayer(MediaPlayer player) {
        if (mediaPlayer == player) return;
        mediaPlayer = player;
        initTimer();
    }


    private void setupVolumeSeekBarListener() {
        binding.volumeSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        changeVolume(seekBar, progress);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
    }

    private void changeVolume(@NonNull SeekBar seekBar, int progress) {
        Log.i(TAG, "Changing volume...");
        float volume = (float) progress / seekBar.getMax();
        setVolume(volume);
    }

    private void setVolume(float volume) {
        mediaPlayer.setVolume(volume, volume);
        binding.volumeSeekBar.setProgress((int) (volume * binding.volumeSeekBar.getMax()));
    }

    private void onPlayButtonClick() {
        //Remember that you have the current state in the attribute "currentState".
        if (currentState == PlayerState.PAUSED) {
            //TODO: resume playback
            resumePlayback();
        } else if (currentState == PlayerState.PLAYING) {
            //TODO: start playback
            startPlayback();
        } else {
            //TODO: start playback
            startPlayback();
        }
    }

    private void onPauseButtonClick() {
        //Remember that you have the current status in the attribute "currentState"
        switch (currentState) {
            case PLAYING:
                //TODO: pause playback
                pausePlayback();
                break;
            case PAUSED:
                //TODO: resume playback
                resumePlayback();
                break;
        }
    }


    /**
     * Changes de Player (activity) state and updates de UI
     */
    private void changePlayerState(PlayerState newState) {
        if (currentState == newState) return;
        currentState = newState;
        updateUI();
    }

    private void updateUI() {
        //binding.playImageButton.setEnabled(currentState != PlayerState.PLAYING);
        //binding.pauseImageButton.setEnabled(currentState != PlayerState.PAUSED);
        binding.volumeSeekBar.setEnabled(currentState != PlayerState.RECORDING);
    }

    /**
     * Starts playback of the MediaPlayer from the beginning of the audio file in looping music.
     * The volume must also be set according to the volume SeekBar progress value
     */
    private void startPlayback() {
        // TODO: set looping to loop playback
        mediaPlayer.setLooping(true);

        // TODO: set volume in right and left channels according to the volume SeekBar progress value
        //changeVolume(binding.volumeSeekBar, binding.volumeSeekBar.getProgress());
        //binding.volumeSeekBar.setProgress(binding.volumeSeekBar.getProgress());
        //mediaPlayer.setVolume(1, 1);
        setVolume((float) binding.volumeSeekBar.getProgress() / binding.volumeSeekBar.getMax());
        //binding.volumeSeekBar.setProgress(binding.volumeSeekBar.getMax());

        // TODO: set playback position in the beginning of the audio track
        mediaPlayer.seekTo(0);

        //TODO: start playback
        mediaPlayer.start();

        //TODO: change state to playing
        changePlayerState(PlayerState.PLAYING);
    }

    /**
     * Pause playback of the audio track
     */
    private void pausePlayback() {

        // TODO: pause playback
        mediaPlayer.pause();

        // TODO: change currentState to paused
        changePlayerState(PlayerState.PAUSED);
    }

    /**
     * Resumes paused playback from the position where it left off when paused
     */
    private void resumePlayback() {

        // TODO: resume paused playback
        //mediaPlayer.seekTo(playbackPosition);
        mediaPlayer.start();

        //TODO: Set currentState to PLAYING
        changePlayerState(PlayerState.PLAYING);
    }

    /**
     * Used to graphically update the time counter / progressbar.
     */
    private void initTimer() {

        timer = () -> showProgressEveryMillis(REFRESH_FREQUENCY_TIME_IN_MILLIS);

        showProgressEveryMillis(REFRESH_FREQUENCY_TIME_IN_MILLIS);
    }

    /**
     * Converts the milliseconds to a text type mm:ss
     */
    private String millisecondsToString(int milliseconds) {
        int seconds = milliseconds / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return getString(R.string.millis_to_string_format, minutes, seconds);
    }

    /**
     * Task called periodically to update song progress (progressbar and textview)
     */
    private void showProgressEveryMillis(int frequencyInMillis) {

        if (mediaPlayer == null) return;

        if (currentState != PlayerState.RECORDING) {

            //TODO: set playbackPosition to the playback position in milliseconds
            playbackPosition = mediaPlayer.getCurrentPosition();
            //TODO: set the duration of the audio track in milliseconds
            int durationInMs = mediaPlayer.getDuration();

            int max = binding.timeProgressBar.getMax();

            int progress = (int) ((float) playbackPosition / durationInMs * max);

            binding.timeProgressBar.setProgress(progress);

            String text =
                    getString(R.string.track_time_position_format,
                            millisecondsToString(playbackPosition),
                            millisecondsToString(durationInMs));

            binding.timeTextView.setText(text);

            // This ensures that the task is executed every 10ms.
            binding.timeTextView.postDelayed(timer, frequencyInMillis);

        } else {
            binding.timeProgressBar.setProgress(0);
            binding.timeTextView.setText(R.string.recording);
        }
    }
}