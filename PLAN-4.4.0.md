# 4.4.0 — Privacy onboarding & visibility UX

## Motivation

Since 4.3.0 the default activity visibility is "Only me" (PRIVATE), but users had no way to
discover this — the setting was buried three taps deep (Me → Settings → Privacy). Users
uploading their first activity had no idea why friends couldn't see it.

## Changes

### 1. Post-login visibility onboarding (`VisibilityOnboardingScreen`)

A one-time screen shown after the first successful login (skipped on subsequent launches).
Displays three selectable tiles — Public / Followers only / Only me — each with a short
description of what it means. "Skip" keeps the PRIVATE default. The chosen visibility is
written immediately to `DefaultVisibilityStore`. Completion is tracked in `OnboardingStore`
(SharedPreferences key `visibility_onboarding_done`).

Flow: `server setup → login → (new) visibility onboarding → main app`

### 2. Server setup tiles (`ServerSetupScreen`)

The existing "Use the official instance" single button is replaced by two side-by-side
`OutlinedButton` tiles: **fitpub.social** and **makni.cz**. Tapping either connects directly
(calls `onSave` with the URL). The free-text field + Connect button stay for custom servers.

### 3. Visibility selector in Edit activity dialog (`ActivityDetailScreen`)

A row of three `FilterChip`s — 🌍 Public / 👥 Followers / 🔒 Only me — added to the
"Edit activity" `AlertDialog`. Initialised from the activity's current visibility when the
dialog opens. Included in the `updateActivity` call on save.

### 4. Visibility icon on "My activities" cards (`ActivityCard`)

`ActivityCard` gains an optional `showVisibility: Boolean = false` parameter. When true
and the activity has a visibility value, a small emoji chip (🔒 / 👥 / 🌍) appears in the
header row beside the Remote badge. `TimelineScreen` passes `true` only for the USER tab.

## Files changed

- `data/settings/OnboardingStore.kt` — new
- `AppContainer.kt` — `onboardingStore` lazy val
- `ui/AppViewModel.kt` — `onboardingStore` ctor arg, `_visibilityOnboardingDone` flow, `markVisibilityOnboardingDone()`, `visibilityOnboardingDone` in `AppUiState`
- `ui/auth/VisibilityOnboardingScreen.kt` — new
- `MainActivity.kt` — `VisibilityOnboardingContent` shown when `loggedIn && !visibilityOnboardingDone`
- `ui/auth/ServerSetupScreen.kt` — two server tiles replace single "official instance" button
- `ui/activity/ActivityDetailScreen.kt` — `editVisibility` state, FilterChip row in Edit dialog
- `ui/components/ActivityCard.kt` — `showVisibility` param, visibility emoji chip
- `ui/timeline/TimelineScreen.kt` — `showVisibility = tab == TimelineTab.USER`
