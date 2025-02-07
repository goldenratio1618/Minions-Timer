package com.example.minionstimer

import android.media.AudioAttributes
import android.media.SoundPool
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

private fun playTone(frequency: Double, durationMs: Int) {
    val sampleRate = 44100
    val numSamples = (durationMs * sampleRate / 1000.0).toInt()
    val samples = ShortArray(numSamples)

    // Generate sine wave samples.
    for (i in 0 until numSamples) {
        val angle = 2.0 * PI * i * frequency / sampleRate
        samples[i] = (sin(angle) * Short.MAX_VALUE).toInt().toShort()
    }

    // Create an AudioTrack instance in static mode.
    val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        samples.size * 2, // each sample is 2 bytes (16 bit)
        AudioTrack.MODE_STATIC
    )

    // Write the generated samples to the AudioTrack buffer.
    audioTrack.write(samples, 0, numSamples)
    audioTrack.play()

    // Optionally, release the AudioTrack after the tone has finished playing.
    // (You might want to postDelayed a release call if the tone duration is long.)
    // For a short beep, you can release it after a short delay:
    Thread {
        Thread.sleep(durationMs.toLong() + 50)
        audioTrack.release()
    }.start()
}

class MainActivity : AppCompatActivity() {

    // States of the timer
    enum class TimerState {
        PRE_GAME, RUNNING, PAUSED, GRACE, TIME_OUT
    }
    // Which team’s turn is active
    enum class Team {
        BLUE, YELLOW
    }

    private var timerState = TimerState.PRE_GAME
    private var currentTeam = Team.YELLOW

    // Team total times (in seconds) as chosen by the user
    private var blueTotalTime = 120 // default value
    private var yellowTotalTime = 120

    // The number of seconds remaining in the current turn (or grace period)
    private var currentRemainingTime = 0

    // Handler and Runnable for our “per‑second” tick
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    // Grace period settings
    private val graceDuration = 5
    private var graceRemaining = graceDuration
    private var graceRunnable: Runnable? = null

    // Views
    private lateinit var timeTextView: TextView
    private lateinit var arrowImageView: ImageView
    private lateinit var pauseButton: Button
    private lateinit var plusButton: Button
    private lateinit var minusButton: Button
    private lateinit var btnAdjustTime: Button
    private lateinit var settingsContainer: View
    private lateinit var timerContainer: View
    private lateinit var blueMinutesEditText: EditText
    private lateinit var blueSecondsEditText: EditText
    private lateinit var yellowMinutesEditText: EditText
    private lateinit var yellowSecondsEditText: EditText
    private lateinit var startButton: Button

    // SoundPool for playing beeps and dings
    private lateinit var soundPool: SoundPool
    private var beepNormalSoundId: Int = 0
    private var beepLowSoundId: Int = 0
    private var beepMediumSoundId: Int = 0
    private var dingSoundId: Int = 0

    // The set of seconds remaining that trigger a “normal” beep
    private val beepTriggerTimes = setOf(60, 30, 10, 5, 4, 3, 2, 1)

    // Add a flag to remember if we paused during grace period:
    private var pausedDuringGrace = false

    private var wasGraceTurn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        setContentView(R.layout.activity_main)
        
        // Find our views (IDs must match those in activity_main.xml)
        timeTextView = findViewById(R.id.timeTextView)
        arrowImageView = findViewById(R.id.arrowImageView)
        pauseButton = findViewById(R.id.pauseButton)
        plusButton = findViewById(R.id.plusButton)
        minusButton = findViewById(R.id.minusButton)
        btnAdjustTime = findViewById(R.id.btnAdjustTime)
        settingsContainer = findViewById(R.id.settingsContainer)
        timerContainer = findViewById(R.id.timerContainer)
        blueMinutesEditText = findViewById(R.id.blueMinutesEditText)
        blueSecondsEditText = findViewById(R.id.blueSecondsEditText)
        yellowMinutesEditText = findViewById(R.id.yellowMinutesEditText)
        yellowSecondsEditText = findViewById(R.id.yellowSecondsEditText)
        startButton = findViewById(R.id.startButton)

        // Initialize SoundPool to play our sound effects
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setAudioAttributes(audioAttributes)
            .setMaxStreams(4)
            .build()
        // (Place your own sound files in res/raw and use the proper names.)
        beepLowSoundId = soundPool.load(this, R.raw.buzzer_sound, 1)
        dingSoundId = soundPool.load(this, R.raw.yellow_start_turn, 1)
        
        // Set up our listeners:
        startButton.setOnClickListener { startGame() }
        pauseButton.setOnClickListener { togglePause() }
        plusButton.setOnClickListener { adjustTime(30) }
        minusButton.setOnClickListener { adjustTime(-30) }
        // In TimerActivity.kt
        btnAdjustTime.setOnClickListener {
            wasGraceTurn = (timerState == TimerState.GRACE)
            // Cancel any running timer or grace period callbacks.
            timerRunnable?.let { handler.removeCallbacks(it) }
            graceRunnable?.let { handler.removeCallbacks(it) }

            // Reset the timer state to PRE_GAME so we can adjust settings.
            timerState = TimerState.PRE_GAME

            // Convert the total times (in seconds) to minutes and seconds.
            val blueMinutes = blueTotalTime / 60
            val blueSeconds = blueTotalTime % 60
            val yellowMinutes = yellowTotalTime / 60
            val yellowSeconds = yellowTotalTime % 60

            // Pre-fill the EditText fields with the current time values.
            blueMinutesEditText.setText(blueMinutes.toString())
            blueSecondsEditText.setText(blueSeconds.toString())
            yellowMinutesEditText.setText(yellowMinutes.toString())
            yellowSecondsEditText.setText(yellowSeconds.toString())

            // Switch the UI back to the time settings screen.
            settingsContainer.visibility = View.VISIBLE
            timerContainer.visibility = View.GONE
        }

        // Tapping anywhere in the timerContainer will end the turn (or resume after timeout)
        timerContainer.setOnClickListener { onTimerTapped() }
    }

    // Called when the user taps “Start” on the settings screen.
    private fun startGame() {
        // Get each team’s time from the EditTexts (in minutes and seconds).
        val blueMinutes = blueMinutesEditText.text.toString().toIntOrNull() ?: 0
        val blueSeconds = blueSecondsEditText.text.toString().toIntOrNull() ?: 0
        val yellowMinutes = yellowMinutesEditText.text.toString().toIntOrNull() ?: 0
        val yellowSeconds = yellowSecondsEditText.text.toString().toIntOrNull() ?: 0

        val blueTotal = blueMinutes * 60 + blueSeconds
        val yellowTotal = yellowMinutes * 60 + yellowSeconds

        // Enforce “max 10 minutes” (600 seconds) per team and a positive time.
        if (blueTotal <= 0 || blueTotal > 600 || yellowTotal <= 0 || yellowTotal > 600) {
            Toast.makeText(this, "Please set a time between 1 and 10 minutes for each team", Toast.LENGTH_SHORT).show()
            return
        }

        blueTotalTime = blueTotal
        yellowTotalTime = yellowTotal

        // Switch the UI back to the timer screen.
        settingsContainer.visibility = View.GONE
        timerContainer.visibility = View.VISIBLE

        // If we were adjusting time during a grace period,
        // start the next turn (flip team) instead of resuming the grace period.
        if (wasGraceTurn) {
            currentTeam = if (currentTeam == Team.BLUE) Team.YELLOW else Team.BLUE
            currentRemainingTime = if (currentTeam == Team.BLUE) blueTotalTime else yellowTotalTime
            arrowImageView.setImageResource(R.drawable.arrow) // restore arrow if needed
            updateArrowIndicator()
            wasGraceTurn = false
            startTurn()
        } else {
            // Otherwise, start normally
            if (currentTeam == Team.BLUE) {
                currentRemainingTime = blueTotalTime
            } else {
                currentRemainingTime = yellowTotalTime
            }
            startTurn()
        }
    }

    // Begins a team’s turn.
    private fun startTurn() {
        timerState = TimerState.RUNNING

        // Play the “ding ding ding” sound.
        soundPool.play(dingSoundId, 1f, 1f, 1, 0, 1f)

        // Update the arrow indicator (its color and rotation) based on which team is active.
        updateArrowIndicator()

        // Start the timer “tick” runnable.
        startTimerRunnable()
    }

    // Update the arrow image: for blue team, point left and tint blue;
    // for yellow team, point right and tint yellow.
    private fun updateArrowIndicator() {
        when (currentTeam) {
            Team.BLUE -> {
                arrowImageView.rotation = 0f // default arrow image points left
                arrowImageView.setColorFilter(resources.getColor(R.color.blue, null))
                timeTextView.setTextColor(resources.getColor(android.R.color.white, null))
            }
            Team.YELLOW -> {
                arrowImageView.rotation = 180f // flip the arrow
                arrowImageView.setColorFilter(resources.getColor(R.color.yellow, null))
                timeTextView.setTextColor(resources.getColor(android.R.color.black, null))
            }
        }
    }


    // Create and start a runnable that decrements the timer each second.
    private fun startTimerRunnable() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = object : Runnable {
            override fun run() {
                if (timerState != TimerState.RUNNING) return

                updateTimeDisplay(currentRemainingTime)

                // If the remaining time is one of the trigger times, play the normal beep.
                if (beepTriggerTimes.contains(currentRemainingTime)) {
                    playTone(1200.0, 100)
                }

                // If time has run out, call onTimeOut() and stop ticking.
                if (currentRemainingTime <= 0) {
                    onTimeOut()
                    return
                }

                currentRemainingTime--
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(timerRunnable!!, 0)
    }

    // Update the large time display (MM:SS).
    private fun updateTimeDisplay(seconds: Int) {
        val minutesPart = seconds / 60
        val secondsPart = seconds % 60
        timeTextView.text = String.format("%02d:%02d", minutesPart, secondsPart)
    }

    // Called when a team’s timer reaches 0.
    private fun onTimeOut() {
        timerState = TimerState.TIME_OUT
        updateTimeDisplay(0)
        soundPool.play(beepLowSoundId, 1f, 1f, 1, 0, 1f)
        // Now the timer “stalls” until the user taps the screen.
    }

    // Handle a tap on the timerContainer.
    private fun onTimerTapped() {
        when (timerState) {
            TimerState.RUNNING -> {
                // Tapping while running ends the turn immediately.
                soundPool.play(beepLowSoundId, 1f, 1f, 1, 0, 1f)
                endTurn()
            }
            TimerState.TIME_OUT -> {
                // If time already ran out, tap to enter the grace period.
                startGracePeriod()  // This should initialize the grace period (only once).
            }
            TimerState.GRACE -> {
                // When in grace period, tapping toggles pause/resume.
                togglePause()
            }
            TimerState.PAUSED -> {
                // A tap in PAUSED state resumes whichever timer (main or grace) was paused.
                togglePause()  // (Assuming resumeTimer() internally calls togglePause())
            }
            else -> { /* do nothing in other states */ }
        }
    }


    // Toggle between pause and resume.
    // Toggle between pause and resume.
    private fun togglePause() {
        when (timerState) {
            TimerState.RUNNING -> {
                timerState = TimerState.PAUSED
                // Remove callbacks from the active runnable.
                timerRunnable?.let { handler.removeCallbacks(it) }
            }
            TimerState.GRACE -> {
                timerState = TimerState.PAUSED
                pausedDuringGrace = true
                graceRunnable?.let { handler.removeCallbacks(it) }
            }
            TimerState.PAUSED -> {
                // Resume based on what was paused.
                if (pausedDuringGrace) {
                    // Resume the grace period countdown without resetting graceRemaining.
                    resumeGracePeriod()
                    pausedDuringGrace = false
                } else {
                    // Resume the normal timer countdown.
                    timerState = TimerState.RUNNING
                    startTimerRunnable()
                }
            }
            else -> { /* do nothing */ }
        }
    }

    // Resumes the grace period using the current remaining time.
    private fun resumeGracePeriod() {
        timerState = TimerState.GRACE
        // Instead of reinitializing graceRemaining, simply post the graceRunnable again.
        graceRunnable?.let { handler.post(it) }
    }

    private fun resumeTimer() {
        timerState = TimerState.RUNNING
        startTimerRunnable()
    }

    // Adjust the current turn’s remaining time by a number of seconds (positive or negative).
    private fun adjustTime(secondsDelta: Int) {
        if (timerState == TimerState.RUNNING || timerState == TimerState.PAUSED) {
            currentRemainingTime += secondsDelta
            if (currentRemainingTime < 0) currentRemainingTime = 0
            updateTimeDisplay(currentRemainingTime)
        }
    }

    // End the current turn (by cancelling the current timer runnable)
    // and begin the grace period.
    private fun endTurn() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        startGracePeriod()
    }

    // Starts the 5‑second grace period.
    private fun startGracePeriod() {
        timerState = TimerState.GRACE

        // Change the UI: display a brown circle (a drawable) instead of the arrow.
        arrowImageView.setImageResource(R.drawable.brown_circle)
        graceRemaining = graceDuration
        updateTimeDisplay(graceRemaining)

        graceRunnable?.let { handler.removeCallbacks(it) }
        graceRunnable = object : Runnable {
            override fun run() {
                if (graceRemaining <= 0) {
                    endGracePeriod()
                } else {
                    updateTimeDisplay(graceRemaining)
                    // Play the medium‑pitched beep every second.
                    playTone(600.0, 100)
                    graceRemaining--
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.postDelayed(graceRunnable!!, 0)
    }

    // Called when the grace period is over.
    // Switch the team and start the next turn.
    private fun endGracePeriod() {
        graceRunnable?.let { handler.removeCallbacks(it) }
        // Flip team.
        currentTeam = if (currentTeam == Team.BLUE) Team.YELLOW else Team.BLUE
        // Set up the new turn’s time from the team’s pre‑set total time.
        currentRemainingTime = if (currentTeam == Team.BLUE) blueTotalTime else yellowTotalTime

        // Restore the arrow image (if you had changed it to the brown circle).
        arrowImageView.setImageResource(R.drawable.arrow)
        startTurn()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        soundPool.release()
    }
}

