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
import kotlin.random.Random

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
    private lateinit var pauseButton: ImageButton
    private lateinit var plusButton: Button
    private lateinit var minusButton: Button
    private lateinit var btnAdjustTime: ImageButton
    private lateinit var settingsContainer: View
    private lateinit var timerContainer: View
    private lateinit var blueTimeDisplay: TextView
    private lateinit var yellowTimeDisplay: TextView
    private lateinit var startButton: Button
    private lateinit var swapButton: ImageButton
    private lateinit var infinityButton: View
    private lateinit var faceButton: Button
    private lateinit var settingsFaceButton: Button
    private lateinit var keypadOverlay: View
    private lateinit var keypadPanel: View
    private lateinit var keypadGrid: GridLayout

    // SoundPool for playing beeps and dings
    private lateinit var soundPool: SoundPool
    private var beepNormalSoundId: Int = 0
    private var beepLowSoundId: Int = 0
    private var beepMediumSoundId: Int = 0
    private var startSoundId: Int = 0
    private var beepSoundId: Int = 0
    private var dingSoundId: Int = 0

    // The set of seconds remaining that trigger a “normal” beep
    private val beepTriggerTimes = setOf(60, 30, 15, 5, 4, 3, 2, 1)

    // Add a flag to remember if we paused during grace period:
    private var pausedDuringGrace = false

    private var wasGraceTurn = false
    private val faceResetRunnables = mutableMapOf<Button, Runnable>()

    private val maxMinutes = 10
    private val faceResetDelayMs = 3000L
    private val maxDigitLength = 4

    private var blueInputDigits = ""
    private var yellowInputDigits = ""
    private var activeInputTeam: Team? = null

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
        blueTimeDisplay = findViewById(R.id.blueTimeDisplay)
        yellowTimeDisplay = findViewById(R.id.yellowTimeDisplay)
        startButton = findViewById(R.id.startButton)
        swapButton = findViewById(R.id.swapButton)
        infinityButton = findViewById(R.id.infinityButton)
        faceButton = findViewById(R.id.faceButton)
        settingsFaceButton = findViewById(R.id.settingsFaceButton)
        keypadOverlay = findViewById(R.id.keypadOverlay)
        keypadPanel = findViewById(R.id.keypadPanel)
        keypadGrid = findViewById(R.id.keypadGrid)

        // Initialize SoundPool to play our sound effects
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setAudioAttributes(audioAttributes)
            .setMaxStreams(4)
            .build()

        // (Place your own sound files in res/raw and use the proper names.)
        beepLowSoundId = soundPool.load(this, R.raw.buzzer_sound, 1)
        startSoundId = soundPool.load(this, R.raw.yellow_start_turn, 1)
        beepSoundId = soundPool.load(this, R.raw.beep, 1)
        dingSoundId = soundPool.load(this, R.raw.ding, 1)
        
        
        // Set up our listeners:
        startButton.setOnClickListener { startGame() }
        swapButton.setOnClickListener { swapTeamTimes() }
        pauseButton.setOnClickListener { togglePause() }
        plusButton.setOnClickListener { addThirtySeconds() }
        minusButton.setOnClickListener { adjustTime(-30) }
        infinityButton.setOnClickListener { addInfinityTime() }
        faceButton.setOnClickListener { triggerRandomFace(faceButton) }
        settingsFaceButton.setOnClickListener { triggerRandomFace(settingsFaceButton) }
        blueTimeDisplay.setOnClickListener { showKeypad(Team.BLUE) }
        yellowTimeDisplay.setOnClickListener { showKeypad(Team.YELLOW) }
        // In TimerActivity.kt
        btnAdjustTime.setOnClickListener {
            wasGraceTurn = (timerState == TimerState.GRACE)
            // Cancel any running timer or grace period callbacks.
            timerRunnable?.let { handler.removeCallbacks(it) }
            graceRunnable?.let { handler.removeCallbacks(it) }

            // Reset the timer state to PRE_GAME so we can adjust settings.
            timerState = TimerState.PRE_GAME

            // Pre-fill the time displays with the current time values.
            syncInputDigitsFromTotals()

            // Switch the UI back to the time settings screen.
            settingsContainer.visibility = View.VISIBLE
            timerContainer.visibility = View.GONE
        }

        // Tapping anywhere in the timerContainer will end the turn (or resume after timeout)
        timerContainer.setOnClickListener { onTimerTapped() }

        setupKeypad()
        syncInputDigitsFromTotals()
    }

    // Called when the user taps “Start” on the settings screen.
    private fun startGame() {
        // Get each team’s time from the digit inputs (MMSS).
        val blueTotal = secondsFromDigits(blueInputDigits)
        val yellowTotal = secondsFromDigits(yellowInputDigits)

        // Enforce “max 10 minutes” (600 seconds) per team and a positive time.
        val maxTotalSeconds = maxMinutes * 60
        if (blueTotal <= 0 || blueTotal > maxTotalSeconds || yellowTotal <= 0 || yellowTotal > maxTotalSeconds) {
            Toast.makeText(this, "Please set a time between 1 and 10 minutes for each team", Toast.LENGTH_SHORT).show()
            return
        }

        hideKeypad()

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
        updatePauseButtonIcon()

        // Play the “ding ding ding” sound.
        soundPool.play(startSoundId, 1f, 1f, 1, 0, 1f)

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
                arrowImageView.rotation = 180f // point right for blue
                arrowImageView.setColorFilter(resources.getColor(R.color.blue, null))
                timeTextView.setTextColor(resources.getColor(android.R.color.white, null))
            }
            Team.YELLOW -> {
                arrowImageView.rotation = 0f // point left for yellow
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
                    soundPool.play(beepSoundId, 1f, 1f, 1, 0, 1f)
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
        updatePauseButtonIcon()
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
        updatePauseButtonIcon()
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
        updatePauseButtonIcon()

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
                    soundPool.play(dingSoundId, 1f, 1f, 1, 0, 1f)
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

    private fun swapTeamTimes() {
        val yellowDigits = yellowInputDigits
        yellowInputDigits = blueInputDigits
        blueInputDigits = yellowDigits
        updateTimeDisplays()
    }

    private fun addThirtySeconds() {
        if (timerState == TimerState.TIME_OUT) {
            currentRemainingTime = 30
            timerState = TimerState.RUNNING
            updateTimeDisplay(currentRemainingTime)
            updatePauseButtonIcon()
            startTimerRunnable()
            return
        }
        adjustTime(30)
    }

    private fun addInfinityTime() {
        val boostSeconds = 5 * 60
        val isGraceLike = timerState == TimerState.GRACE || (timerState == TimerState.PAUSED && pausedDuringGrace)
        when {
            isGraceLike || timerState == TimerState.TIME_OUT -> {
                timerRunnable?.let { handler.removeCallbacks(it) }
                graceRunnable?.let { handler.removeCallbacks(it) }
                pausedDuringGrace = false
                currentRemainingTime = boostSeconds
                timerState = TimerState.RUNNING
                arrowImageView.setImageResource(R.drawable.arrow)
                updateArrowIndicator()
                updateTimeDisplay(currentRemainingTime)
                updatePauseButtonIcon()
                startTimerRunnable()
            }
            timerState == TimerState.RUNNING || timerState == TimerState.PAUSED -> {
                currentRemainingTime += boostSeconds
                updateTimeDisplay(currentRemainingTime)
            }
            else -> { /* do nothing */ }
        }
    }

    private fun triggerRandomFace(target: Button) {
        val face = if (Random.nextBoolean()) "🙂" else "🙁"
        target.text = face
        target.isEnabled = false
        faceResetRunnables[target]?.let { handler.removeCallbacks(it) }
        val resetRunnable = Runnable {
            target.text = "🤔"
            target.isEnabled = true
        }
        faceResetRunnables[target] = resetRunnable
        handler.postDelayed(resetRunnable, faceResetDelayMs)
    }

    private fun updatePauseButtonIcon() {
        val isPaused = timerState == TimerState.PAUSED
        pauseButton.setImageResource(if (isPaused) R.drawable.ic_play else R.drawable.ic_pause)
        pauseButton.contentDescription = if (isPaused) "Play" else "Pause"
    }

    private fun setupKeypad() {
        keypadOverlay.setOnClickListener { hideKeypad() }
        keypadPanel.setOnClickListener { }
        for (i in 0 until keypadGrid.childCount) {
            val child = keypadGrid.getChildAt(i)
            val tag = child.tag?.toString() ?: continue
            child.setOnClickListener { handleKeypadInput(tag) }
        }
    }

    private fun showKeypad(team: Team) {
        activeInputTeam = team
        if (team == Team.BLUE) {
            blueInputDigits = ""
        } else {
            yellowInputDigits = ""
        }
        updateTimeDisplays()
        keypadOverlay.visibility = View.VISIBLE
    }

    private fun hideKeypad() {
        keypadOverlay.visibility = View.GONE
        activeInputTeam = null
    }

    private fun handleKeypadInput(input: String) {
        val team = activeInputTeam ?: return
        if (input == "enter") {
            hideKeypad()
            return
        }
        val currentDigits = if (team == Team.BLUE) blueInputDigits else yellowInputDigits
        val updatedDigits = when (input) {
            "backspace" -> currentDigits.dropLast(1)
            "00" -> when {
                currentDigits.length <= maxDigitLength - 2 -> currentDigits + "00"
                currentDigits.length == maxDigitLength - 1 -> currentDigits + "0"
                else -> currentDigits
            }
            else -> {
                if (currentDigits.length < maxDigitLength) currentDigits + input else currentDigits
            }
        }

        val clampedDigits = clampDigitsToMax(updatedDigits)
        if (team == Team.BLUE) {
            blueInputDigits = clampedDigits
        } else {
            yellowInputDigits = clampedDigits
        }
        updateTimeDisplays()
    }

    private fun clampDigitsToMax(digits: String): String {
        val totalSeconds = secondsFromDigits(digits)
        if (totalSeconds <= maxMinutes * 60) {
            return digits
        }
        return digitsFromSeconds(maxMinutes * 60)
    }

    private fun secondsFromDigits(digits: String): Int {
        val trimmed = digits.takeLast(maxDigitLength)
        val padded = trimmed.padStart(maxDigitLength, '0')
        val minutes = padded.substring(0, 2).toInt()
        val seconds = padded.substring(2, 4).toInt()
        return minutes * 60 + seconds
    }

    private fun digitsFromSeconds(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d%02d", minutes, seconds)
    }

    private fun updateTimeDisplays() {
        blueTimeDisplay.text = formatDigits(blueInputDigits)
        yellowTimeDisplay.text = formatDigits(yellowInputDigits)
    }

    private fun formatDigits(digits: String): String {
        val trimmed = digits.takeLast(maxDigitLength)
        val padded = trimmed.padStart(maxDigitLength, '0')
        val minutes = padded.substring(0, 2)
        val seconds = padded.substring(2, 4)
        return "$minutes:$seconds"
    }

    private fun syncInputDigitsFromTotals() {
        blueInputDigits = digitsFromSeconds(blueTotalTime)
        yellowInputDigits = digitsFromSeconds(yellowTotalTime)
        updateTimeDisplays()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        soundPool.release()
    }
}
