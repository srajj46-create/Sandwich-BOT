package com.sandwichbot

object Config {
    // Tap latency compensation (ms): bada karo agar bot der se tap kar raha hai
    @Volatile var leadMs = 110
    // Target x position in permille of screen width (500 = bilkul beech)
    @Volatile var targetPm = 500
}
