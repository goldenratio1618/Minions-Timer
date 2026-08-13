(function (root, factory) {
  const core = factory();
  if (typeof module === "object" && module.exports) module.exports = core;
  root.TimerCore = core;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const MAX_MINUTES = 10;
  const MAX_SECONDS = MAX_MINUTES * 60;
  const MAX_DIGITS = 4;

  function secondsFromDigits(value) {
    const digits = String(value || "").replace(/\D/g, "").slice(-MAX_DIGITS).padStart(MAX_DIGITS, "0");
    return Number(digits.slice(0, 2)) * 60 + Number(digits.slice(2));
  }

  function digitsFromSeconds(value) {
    const seconds = Math.max(0, Math.min(MAX_SECONDS, Math.floor(Number(value) || 0)));
    return `${String(Math.floor(seconds / 60)).padStart(2, "0")}${String(seconds % 60).padStart(2, "0")}`;
  }

  function clampDigits(value) {
    const digits = String(value || "").replace(/\D/g, "").slice(0, MAX_DIGITS);
    return secondsFromDigits(digits) <= MAX_SECONDS ? digits : digitsFromSeconds(MAX_SECONDS);
  }

  function formatDigits(value) {
    const digits = String(value || "").replace(/\D/g, "").slice(-MAX_DIGITS).padStart(MAX_DIGITS, "0");
    return `${digits.slice(0, 2)}:${digits.slice(2)}`;
  }

  function formatSeconds(value) {
    const seconds = Math.max(0, Math.ceil(Number(value) || 0));
    return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
  }

  function remainingSeconds(deadline, now) {
    return Math.max(0, Math.ceil((Number(deadline) - Number(now)) / 1000));
  }

  function oppositeTeam(team) {
    return team === "blue" ? "yellow" : "blue";
  }

  function createAudioCoordinator(resolveAudio, soundIds) {
    let generation = 0;

    function resetAudio(audio) {
      if (!audio) return;
      audio.pause();
      try {
        audio.currentTime = 0;
      } catch (_error) {
        // Some browsers reject seeking until media metadata is available.
      }
    }

    function stopAll() {
      generation += 1;
      soundIds.forEach((id) => resetAudio(resolveAudio(id)));
    }

    function play(id) {
      stopAll();
      const audio = resolveAudio(id);
      if (!audio) return;
      const playGeneration = generation;
      let result;
      try {
        result = audio.play();
      } catch (_error) {
        return;
      }
      if (result && typeof result.then === "function") {
        result.then(() => {
          if (playGeneration !== generation) resetAudio(audio);
        }).catch(() => {});
      }
    }

    return { play, stopAll };
  }

  return {
    MAX_DIGITS,
    MAX_MINUTES,
    MAX_SECONDS,
    clampDigits,
    createAudioCoordinator,
    digitsFromSeconds,
    formatDigits,
    formatSeconds,
    oppositeTeam,
    remainingSeconds,
    secondsFromDigits,
  };
});
