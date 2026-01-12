# Pulse

**Offline, privacy-preserving survivor presence detection using ambient smartphone signals**

[![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF)](https://kotlinlang.org/)
[![Platforms](https://img.shields.io/badge/Platforms-Android%20%7C%20iOS-blue)](#)
[![License](https://img.shields.io/badge/License-Apache%202.0-green)](LICENSE)
[![Contest](https://img.shields.io/badge/Kotlin%20Student%20Contest-2026-orange)](#)
[![Status](https://img.shields.io/badge/Status-Prototype-critical)](#)

---

## Overview

When disasters strike, **communication fails first**.

Survivors may be unconscious, trapped, or unable to interact with their phones. Networks collapse. Infrastructure is unreliable. Most emergency systems assume *active participation* — tapping a screen, sending a signal, opening an app.

**Pulse is built on a different assumption**:

> *Human presence can be inferred without user interaction, internet access, or identity.*

Pulse is a **phone-only Android + iOS application** that passively detects *probable human presence* by analyzing unavoidable ambient radio emissions already produced by smartphones (e.g. Bluetooth Low Energy advertisements).

No accounts. No pairing. No cloud. No identification.

---

## Why Pulse Is Novel

Pulse does **not** attempt communication, tracking, or identification.

Instead, it treats phones as **passive beacons**, similar to RFID, and models *signal behavior over time* to infer whether a human is likely present nearby.

**Key differences from existing approaches:**

| Traditional Systems        | Pulse                           |
| -------------------------- | ------------------------------- |
| Require user interaction   | Fully passive                   |
| Depend on networks         | Fully offline                   |
| Identify devices or people | Detects presence only           |
| One-shot pings             | Time-based persistence modeling |
| Server-centric             | On-device only                  |

Pulse focuses on **determinism and signal physics**, not machine-learning guesswork.

---

## Core Idea

Modern smartphones continuously emit short-range radio signals even when locked.

Pulse observes these signals and evaluates:

* **Persistence** — does a signal remain over time?
* **Stability** — does it decay naturally or fluctuate erratically?
* **Clustering** — do multiple signals form spatial/temporal groups?
* **Motion behavior** — stationary vs transient sources

A single ping is meaningless.
**Consistent behavior over time is not.**

---

## System Architecture

![Pulse system architecture](assets/systems.png)

Pulse is built using **Kotlin Multiplatform** with a strict separation of concerns.

### Shared Core (Kotlin Multiplatform — `commonMain`)

All detection intelligence lives in shared Kotlin code:

* Signal normalization
* Rolling time windows
* Presence confidence scoring
* Temporal decay modeling
* Motion classification
* Spatial & temporal clustering
* Deterministic scoring logic

This code is compiled **unchanged** for both Android and iOS.

### Platform Layers (Thin, Ingest-Only)

* **Android**: Bluetooth LE scanning & permissions
* **iOS**: CoreBluetooth scanning (within OS constraints)

Platform code is limited to **signal ingestion only**.
No detection logic is duplicated.

---

## Offline & Privacy Guarantees

Pulse enforces hard constraints by design:

* No internet access
* No servers
* No analytics
* No device identifiers
* No MAC address storage
* No persistent data across restarts

All signal data is ephemeral and cleared on app restart.

Pulse detects **presence**, not **people**.

---

## Demo Scenario (How to Evaluate)

Pulse is designed to be judged through simple, reproducible setups:

1. Place two stationary phones in a room
   → A stable cluster forms over time

2. Walk through the room with a phone
   → Classified as unstable / transient

3. Turn off a stationary phone
   → Signal decays gradually, not instantly

4. Enable airplane mode + Bluetooth only
   → Detection continues fully offline

---

## Why Kotlin Multiplatform Was Essential

Pulse’s correctness depends on **identical behavior across platforms**.

Kotlin Multiplatform enables:

* One shared signal model
* One scoring algorithm
* One clustering implementation
* Identical behavior on Android and iOS

This avoids divergence, reduces bugs, and ensures fairness and determinism — critical for safety-oriented systems.

---

## Project Structure

```
.
├── composeApp/
│   └── src/
│       ├── commonMain/      # Shared detection core (KMP)
│       ├── androidMain/     # Android BLE ingestion
│       └── iosMain/         # iOS BLE ingestion
├── iosApp/                  # iOS app entry point
├── assets/
│   └── systems.png          # Architecture diagram
└── README.md
```

---

## Build & Run

### Android

```bash
./gradlew :composeApp:assembleDebug
```

Run on a physical Android device with Bluetooth enabled.

### iOS

Open `iosApp/` in Xcode and run on a physical iPhone.

> Note: iOS simulator does not emit real Bluetooth signals.
> Use a real device for meaningful results.

---

## Project Status

Pulse is an **early-stage prototype** focused on:

* Correctness
* Determinism
* Clear demonstration of passive detection

Future work may explore additional signal sources and improved visualization, but the core detection model is intentionally simple and explainable.

---

## License

Apache 2.0

---

## Why This Matters

In disaster response, **doing nothing should not mean being invisible**.

Pulse explores how existing devices can quietly signal life —
even when their owners cannot.

---

If you want, I can also:

* **Tighten this further to exactly ~300 words for the essay**
* **Add a “Judge Quick Start” section**
* **Rewrite the intro to be even more competition-optimized**
* **Cross-check wording against past winning Kotlin projects**

Just tell me what to tweak.
