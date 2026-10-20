# 4.1 validation

Minecraft 26.2, Java 25 and Fabric requirements remain the same as 4.0.

- **245 core and 3 mod unit tests passed.** Study checks cover answer validation, all 72 bundled questions, review intervals, exam scheduling including Methods Exam 2, connection-scoped credits, cancellation, atomic persistence and preserving invalid saves.
- **All 146 required server GameTests passed**, covering existing combat and game systems.
- **Native UI checks** exercised all 11 main menu destinations and the editor's Layout, Items and Details pages at GUI sizes 320×240, 400×225, 480×270, 640×360 and 640×480. Checks inspect visible text and control bounds while scrolling; contained button labels and transparent accessibility hitboxes are treated explicitly. Fresh screenshots were also reviewed to catch painting errors, including the editor border size bug.
- **The native study scenario passed:** a wrong answer, progressive hints, an assisted correction, saved progress, the entry question's first-fight credit, another question before the next actual match, safe cancellation, optional study and written self-review marked Needs work.
- **The existing 4.0 client scenario passed**, including menu navigation and settings, career progression, recording and replay controls, terrain rematch, and saving an enchanted custom kit at a compact GUI size.
- **The native shutdown scenario passed**, closing two worlds with live bots and checking that each integrated server stopped and the bot manager retained no bots. This does not exercise Lunar's launcher itself.

Client runs used Xvfb and software OpenGL. The final jar's main, client and nested core classes are compared byte for byte with the tested compiled output before publication; the GitHub download is checked against the local jar's SHA-256.

See the [study guide](study-4.1.md) for question scope, the supplied exam timetable, screenshots and configuration. Replace the previous SparBot jar rather than installing both versions.
