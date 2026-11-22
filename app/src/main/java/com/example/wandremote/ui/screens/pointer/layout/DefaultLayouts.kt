package com.example.wandremote.ui.screens.pointer.layout

import com.example.wandremote.settings.AppSettings

/**
 * The built-in Pointer layouts. All numbers are in the normalized units of [ControlShape]; vertical positions are
 * written in "width units" (a length as a fraction of the width) and divided by the content's aspect
 * (height / width) to become y fractions, so the bottom-anchored cluster keeps its physical size on any phone.
 *
 * Right-handed arrangement (W = 1 is the content width, y measured from the top in width units):
 *  - bottom-right: scroll knob, click ring (left click = lower half next to the screen edge, right click = upper
 *    half) and, above the ring at the screen edge, the movement toggle;
 *  - laser: a large rounded button above-left of the click ring, left of the toggle;
 *  - arrows: a compact cross in the left strip (x < 0.36), around a third of the way down;
 *  - the top strip is free: the status text lives there.
 */
object DefaultLayouts {
    /** Typical height / width of the content area on a 9:19.5 phone, used when the real size is not known. */
    const val TYPICAL_ASPECT = 1.85f

    private const val RING_GAP = 0.025f
    private const val TOGGLE_R = 0.085f
    private const val ARROW_SIZE = 0.11f
    private const val ARROW_GAP = 0.012f
    private const val TOP_CLEARANCE = 0.14f

    /**
     * The default arrangement for right-handed use. [knobFraction] is the knob radius
     * ([AppSettings.knobSizeFraction]); [aspect] is content height / width.
     */
    fun rightHanded(
        knobFraction: Float = AppSettings.DEFAULT_KNOB_FRACTION,
        aspect: Float = TYPICAL_ASPECT,
    ): PointerLayout {
        val k = knobFraction
        val a = aspect
        fun y(widthUnits: Float) = widthUnits / a

        val cx = 1f - k
        val cyU = a - k // knob center, width units from the top
        val ringIn = k + RING_GAP
        val ringOut = ringIn + k
        val ringTopU = cyU - ringOut

        // Movement toggle: above the ring, at the right screen edge.
        val toggleCyU = ringTopU - 1.5f * RING_GAP - TOGGLE_R

        // Laser: left of the toggle, above the ring.
        val laserBottomU = ringTopU - 0.015f
        val laserTopU = laserBottomU - 0.26f

        // Arrow cross in the left strip, kept clear of the status text and the click ring.
        val step = ARROW_SIZE + ARROW_GAP
        val arrowCx = 0.01f + step * 1.5f
        val ringUpperLeftU = cyU - ringOut * 0.695f // y of the left-click sector's upper corner (sin 44 deg)
        val halfCross = step * 1.5f
        val arrowCyU = (0.34f * a + 0.02f)
            .coerceAtMost(ringUpperLeftU - 0.02f - halfCross)
            .coerceAtLeast(TOP_CLEARANCE + 0.06f + halfCross)

        fun arrow(id: ControlId, dx: Float, dyU: Float, label: String): ControlSpec {
            val ccx = arrowCx + dx * step
            val ccy = arrowCyU + dyU * step
            val h = ARROW_SIZE / 2f
            return ControlSpec(
                id,
                ControlShape.RoundRect(ccx - h, y(ccy - h), ccx + h, y(ccy + h), 0.03f),
                label,
            )
        }

        return PointerLayout(
            listOf(
                ControlSpec(ControlId.KNOB, ControlShape.Circle(cx, y(cyU), k)),
                ControlSpec(
                    ControlId.LEFT_CLICK,
                    ControlShape.RingSector(cx, y(cyU), ringIn, ringOut, startDeg = 176f, sweepDeg = 48f),
                    "L",
                ),
                ControlSpec(
                    ControlId.RIGHT_CLICK,
                    ControlShape.RingSector(cx, y(cyU), ringIn, ringOut, startDeg = 226f, sweepDeg = 48f),
                    "R",
                ),
                ControlSpec(ControlId.MOVE_TOGGLE, ControlShape.Circle(1f - TOGGLE_R, y(toggleCyU), TOGGLE_R)),
                ControlSpec(
                    ControlId.LASER,
                    ControlShape.RoundRect(0.40f, y(laserTopU), 0.74f, y(laserBottomU), 0.06f),
                    "Hold for laser",
                ),
                arrow(ControlId.ARROW_UP, 0f, -1f, "UP"),
                arrow(ControlId.ARROW_LEFT, -1f, 0f, "LEFT"),
                arrow(ControlId.ARROW_RIGHT, 1f, 0f, "RIGHT"),
                arrow(ControlId.ARROW_DOWN, 0f, 1f, "DOWN"),
            ),
        )
    }

    /** [rightHanded] for the given content aspect, mirrored when [flipped] (the "flip button layout" setting). */
    fun forScreen(knobFraction: Float, aspect: Float, flipped: Boolean): PointerLayout {
        val base = rightHanded(knobFraction, aspect)
        return if (flipped) base.mirrored() else base
    }
}
