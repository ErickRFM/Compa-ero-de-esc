# Motion Matrix V4

The app uses motion to explain state, hierarchy and navigation. Decorative motion
must never obscure information or remain active without purpose.

| Surface | Trigger | Motion | Haptic | Reduced motion |
|---|---|---|---|---|
| App shell | launch | stagger header -> hero -> content | none | short fade |
| Login | submit | CTA progress -> success -> shell | confirm on success | crossfade |
| Bottom nav | destination | pill translates + icon scale | none | color/fade |
| Hoy hero | time progress | animated progress bar | none | static progress |
| Current class | occurrence changes | AnimatedContent/morph | none | crossfade |
| Home -> class | tap | shared bounds/title/time | tick optional | fade/scale |
| Back gesture | predictive back | reverse shared bounds | none | platform default |
| Agenda day | swipe/tap | directional slide | selection tick | crossfade |
| Día/Semana | toggle | moving segmented pill | tick | color/fade |
| Offline | connection lost | notice slides/morphs in | none | fade |
| Sync | pending -> synced | status morph | confirm when user initiated | text/icon swap |
| Attendance opens | server state | hero morph + subtle CTA pulse | tick | static CTA |
| QR scanner | detection | frame pulse -> verify | click | icon swap |
| Attendance verified | success | check spring + small burst | confirm | check fade |
| Review required | result | success surface morph -> review | warning | instant |
| Rejected | result | controlled shake/status change | reject | no shake |
| Notification badge | count change | scale in/out | none | instant |

## Motion tokens

Durations:
- Fast: 120ms
- Standard: 220ms
- Emphasized: 320ms
- Hero: 450ms

Spring roles:
- Snappy: navigation selection, chips.
- Standard: cards and common state changes.
- Soft: banners and large surface movement.
- Bouncy: success-only accents; never body/navigation motion.

## Constraints

- no infinite motion on information-dense screens except subtle progress/scan affordances;
- respect system animator scale and app reduced-motion policy;
- one dominant motion per interaction;
- no full-screen parallax on low-end devices;
- particle effects capped and one-shot;
- animations must be interruptible by user navigation.
