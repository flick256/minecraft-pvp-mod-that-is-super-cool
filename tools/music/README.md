# Vaelor's Theme

`VaelorTheme.java` renders the boss theme of the Heartwell (`src/main/resources/assets/sparbot/sounds/music/vaelor.ogg`):
an original piece, synthesised from scratch (no samples): 150 bpm in D minor, 60 bars (96 s), stereo.

    java VaelorTheme.java vaelor.wav
    ffmpeg -i vaelor.wav -c:a libvorbis -q:a 3 vaelor.ogg

If you change its length, change `VaelorFight.THEME_TICKS` to match (it is played again when it ends).
