package com.example.wandremote.ui.screens.pointer.layout

/** One control: what it is ([id]), where/how big ([shape]) and its optional caption ([label]). */
data class ControlSpec(val id: ControlId, val shape: ControlShape, val label: String? = null)

/**
 * The arrangement of all Pointer-screen controls. The list order is the z-order: later entries are drawn on top and
 * win [hitTest]. Coordinates follow the convention documented on [ControlShape].
 *
 * Future layout editor: the renderer and the touch dispatcher (`PointerControls`) only ever read a [PointerLayout]
 * value, so an editor just has to produce a different one (move or reshape a [ControlSpec], reorder the list),
 * persist it (all fields are plain numbers, strings and enums, so JSON works) and pass it in instead of
 * [DefaultLayouts.forScreen]. "Flip layout" is [mirrored] on whatever layout is in use. No rendering or
 * hit-testing code has to change.
 */
data class PointerLayout(val controls: List<ControlSpec>) {
    init {
        require(controls.map { it.id }.toSet().size == controls.size) { "duplicate control ids" }
    }

    operator fun get(id: ControlId): ControlSpec? = controls.firstOrNull { it.id == id }

    /**
     * The layout flipped horizontally. Directional controls keep their meaning: ARROW_LEFT and ARROW_RIGHT swap
     * shapes afterwards, so the cross moves to the other side but its left arrow is still its left-hand button.
     * Click-ring sectors keep their ids (the left click stays the half nearest the screen edge).
     */
    fun mirrored(): PointerLayout {
        val flipped = controls.map { it.copy(shape = it.shape.mirrored()) }
        val left = flipped.firstOrNull { it.id == ControlId.ARROW_LEFT }
        val right = flipped.firstOrNull { it.id == ControlId.ARROW_RIGHT }
        if (left == null || right == null) return PointerLayout(flipped)
        return PointerLayout(
            flipped.map {
                when (it.id) {
                    ControlId.ARROW_LEFT -> it.copy(shape = right.shape)
                    ControlId.ARROW_RIGHT -> it.copy(shape = left.shape)
                    else -> it
                }
            },
        )
    }

    /** The topmost control containing pixel ([px], [py]) in an area of [widthPx] x [heightPx], or null. */
    fun hitTest(px: Float, py: Float, widthPx: Float, heightPx: Float): ControlId? {
        for (i in controls.indices.reversed()) {
            val c = controls[i]
            if (c.shape.contains(px, py, widthPx, heightPx)) return c.id
        }
        return null
    }
}
