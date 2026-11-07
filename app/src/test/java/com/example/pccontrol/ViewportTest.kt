package com.example.pccontrol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportTest {
    private val eps = 1e-4f

    @Test
    fun noZoom_isFullScreen() {
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 1f), streamViewport(1f, 0.5f, 0.5f), eps)
    }

    @Test
    fun zoom2_centered() {
        assertArrayEquals(floatArrayOf(0.25f, 0.25f, 0.75f, 0.75f), streamViewport(2f, 0.5f, 0.5f), eps)
    }

    @Test
    fun zoom_isClampedToScreenEdges() {
        val v = streamViewport(2f, 0.05f, 0.95f)
        assertArrayEquals(floatArrayOf(0f, 0.5f, 0.5f, 1f), v, eps)
    }

    @Test
    fun zoom_isLimitedTo6x() {
        val v = streamViewport(50f, 0.5f, 0.5f)
        assertEquals(1f / 6f, v[2] - v[0], eps)
    }

    @Test
    fun layer_isIdentityWhenFrameMatchesView() {
        val v = streamViewport(3f, 0.4f, 0.6f)
        assertArrayEquals(floatArrayOf(1f, 1f, 0f, 0f), streamViewLayer(v, v), eps)
    }

    @Test
    fun layer_scalesFullFrameToZoomedView() {
        val lay = streamViewLayer(streamViewport(2f, 0.5f, 0.5f), floatArrayOf(0f, 0f, 1f, 1f))
        assertEquals(2f, lay[0], eps)
        assertEquals(2f, lay[1], eps)
        assertEquals(0f, lay[2], eps)
        assertEquals(0f, lay[3], eps)
    }

    @Test
    fun layer_pointStaysUnderFinger() {
        // Любая точка экрана ПК должна оказаться там, где её ждёт область view, при любом кадре f.
        val view = streamViewport(2.5f, 0.3f, 0.7f)
        val frame = floatArrayOf(0.1f, 0.4f, 0.6f, 0.9f)
        val lay = streamViewLayer(view, frame)
        val sx = 0.35f
        val uFrame = (sx - frame[0]) / (frame[2] - frame[0])
        val displayed = 0.5f + (uFrame - 0.5f) * lay[0] + lay[2]
        val expected = (sx - view[0]) / (view[2] - view[0])
        assertEquals(expected, displayed, eps)
    }

    @Test
    fun tapMapping_roundTrip() {
        val v = streamViewport(4f, 0.8f, 0.2f)
        val u = 0.5f
        val screenX = v[0] + u * (v[2] - v[0])
        assertTrue(screenX in v[0]..v[2])
        assertEquals(u, (screenX - v[0]) / (v[2] - v[0]), eps)
    }
}
