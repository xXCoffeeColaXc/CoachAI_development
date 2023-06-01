package hu.bme.aut.android.coachai.listener

import hu.bme.aut.android.coachai.swing.Swing

interface SwingObserver {
    fun onSwingDetected(swing: Swing)
}
