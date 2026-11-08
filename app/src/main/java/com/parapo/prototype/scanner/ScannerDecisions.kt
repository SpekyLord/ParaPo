package com.parapo.prototype.scanner

import com.parapo.prototype.data.JourneyMatch
import com.parapo.prototype.data.RouteCatalog
import com.parapo.prototype.data.VerificationStatus
import com.parapo.prototype.data.normalizeAlias
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

data class SignBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = max(0f, width) * max(0f, height)
}

fun unletterbox(box: SignBox, imageWidth: Int, imageHeight: Int, inputSize: Int = 320): SignBox {
    if (imageWidth <= 0 || imageHeight <= 0) return SignBox(0f, 0f, 0f, 0f)
    val scale = min(inputSize.toFloat() / imageWidth, inputSize.toFloat() / imageHeight)
    val padX = (inputSize - imageWidth * scale) / 2f
    val padY = (inputSize - imageHeight * scale) / 2f
    return clipBox(
        SignBox((box.left - padX) / scale, (box.top - padY) / scale,
            (box.right - padX) / scale, (box.bottom - padY) / scale),
        imageWidth.toFloat(), imageHeight.toFloat(),
    )
}

fun clipBox(box: SignBox, width: Float, height: Float): SignBox = SignBox(
    box.left.coerceIn(0f, width), box.top.coerceIn(0f, height),
    box.right.coerceIn(0f, width), box.bottom.coerceIn(0f, height),
)

fun toPreviewBox(box: SignBox, frameWidth: Int, frameHeight: Int, viewWidth: Int, viewHeight: Int): SignBox {
    if (frameWidth <= 0 || frameHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return SignBox(0f, 0f, 0f, 0f)
    val scale = max(viewWidth.toFloat() / frameWidth, viewHeight.toFloat() / frameHeight)
    val offsetX = (viewWidth - frameWidth * scale) / 2f
    val offsetY = (viewHeight - frameHeight * scale) / 2f
    return SignBox(
        box.left * scale + offsetX, box.top * scale + offsetY,
        box.right * scale + offsetX, box.bottom * scale + offsetY,
    )
}

fun intersectionOverUnion(a: SignBox, b: SignBox): Float {
    val overlap = max(0f, min(a.right, b.right) - max(a.left, b.left)) *
        max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
    val union = a.area + b.area - overlap
    return if (union <= 0f) 0f else overlap / union
}

data class SignDetection(val box: SignBox, val score: Float)

fun decodeYoloOutput(
    value: Any,
    imageWidth: Int,
    imageHeight: Int,
    scoreThreshold: Float = 0.25f,
    inputSize: Int = 320,
): List<SignDetection> {
    val batch = when (value) {
        is Array<*> -> value.firstOrNull() as? Array<*> ?: return emptyList()
        else -> return emptyList()
    }
    val features = batch.mapNotNull { it as? FloatArray }
    if (features.isEmpty()) return emptyList()
    val channelsFirst = features[0].size > 8 || (features.size in 5..8 && features[0].size < features.size)
    if (if (channelsFirst) features.size < 5 else features.any { it.size < 5 }) return emptyList()
    val count = if (channelsFirst) features[0].size else features.size
    val raw = (0 until count).mapNotNull { index ->
        val get = if (channelsFirst) { channel: Int -> features.getOrNull(channel)?.getOrNull(index) }
            else { channel: Int -> features.getOrNull(index)?.getOrNull(channel) }
        val cx = get(0) ?: return@mapNotNull null
        val cy = get(1) ?: return@mapNotNull null
        val width = get(2) ?: return@mapNotNull null
        val height = get(3) ?: return@mapNotNull null
        val score = get(4) ?: return@mapNotNull null
        if (!score.isFinite() || score < scoreThreshold || !cx.isFinite() || !cy.isFinite() ||
            !width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f
        ) return@mapNotNull null
        SignDetection(SignBox(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f), score)
    }.sortedByDescending(SignDetection::score)

    val kept = mutableListOf<SignDetection>()
    for (detection in raw) {
        if (kept.none { intersectionOverUnion(it.box, detection.box) > 0.7f }) kept += detection
        if (kept.size == 300) break
    }
    return kept.mapNotNull { detection ->
        val clipped = unletterbox(detection.box, imageWidth, imageHeight, inputSize)
        if (clipped.area <= 0f) null else SignDetection(clipped, detection.score)
    }
}

class SingleSignTarget {
    private var sequence = 0
    var selectedId: String? = null
        private set
    var selectedBox: SignBox? = null
        private set

    fun select(box: SignBox): String {
        selectedId = "target-${++sequence}"
        selectedBox = box
        return selectedId!!
    }

    fun associate(boxes: List<SignBox>): SignBox? {
        val previous = selectedBox ?: return null
        if (boxes.isEmpty()) return null.also { clear() }
        val ranked = boxes.map { it to intersectionOverUnion(previous, it) }.sortedByDescending { it.second }
        if (ranked.first().second < MIN_ASSOCIATION_IOU ||
            (ranked.size > 1 && ranked[0].second - ranked[1].second < AMBIGUITY_MARGIN)
        ) return null.also { clear() }
        selectedBox = ranked.first().first
        return selectedBox
    }

    fun clear() {
        selectedId = null
        selectedBox = null
    }

    private companion object {
        const val MIN_ASSOCIATION_IOU = 0.2f
        const val AMBIGUITY_MARGIN = 0.08f
    }
}

enum class DirectionSource { SIGN, USER }

enum class DecisionKind {
    MATCH, NO_MATCH, CONFIRM_DIRECTION, UNREADABLE, UNSUPPORTED, UNCERTAIN, IDENTIFIED,
}

data class ScannerDecision(
    val kind: DecisionKind,
    val routeId: String? = null,
    val routeName: String? = null,
    val patternId: String? = null,
    val direction: String? = null,
    val directionSource: DirectionSource? = null,
) {
    val signature: String get() = listOf(kind, routeId, patternId, directionSource).joinToString("|")

    val message: String get() = when (kind) {
        DecisionKind.MATCH -> "✓ Matches your selected journey"
        DecisionKind.NO_MATCH -> "✕ No direct match for this journey"
        DecisionKind.CONFIRM_DIRECTION -> "? Route identified — confirm direction"
        DecisionKind.UNREADABLE -> "? Cannot read route — try again or select manually"
        DecisionKind.UNSUPPORTED -> "? Route outside verified coverage"
        DecisionKind.UNCERTAIN -> "! Uncertain — select one sign and confirm route"
        DecisionKind.IDENTIFIED -> "ℹ Route identified"
    }
}

sealed interface RouteTextResolution {
    data class Found(val routeId: String) : RouteTextResolution
    data class Ambiguous(val routeIds: Set<String>) : RouteTextResolution
    data object Unsupported : RouteTextResolution
    data object Unreadable : RouteTextResolution
}

object ScannerDecisions {
    fun routeFromText(text: String, catalog: RouteCatalog): RouteTextResolution {
        val normalized = " ${normalizeText(text)} "
        val matches = catalog.routes.filter { route ->
            val aliases = listOf(route.name) + route.aliases
            aliases.any { alias ->
                val normalizedAlias = normalizeText(alias)
                normalizedAlias.isNotEmpty() && normalized.contains(" $normalizedAlias ")
            } || routeNumbers(aliases).any { number ->
                Regex("(?<![a-z0-9])${Regex.escape(number)}(?![a-z0-9])").containsMatchIn(normalized)
            }
        }.map { it.id }.toSet()
        return when (matches.size) {
            1 -> RouteTextResolution.Found(matches.first())
            in 2..Int.MAX_VALUE -> RouteTextResolution.Ambiguous(matches)
            else -> if (Regex("(?<![a-z0-9])(?:route\\s*)?\\d+(?![a-z0-9])").containsMatchIn(normalized)) {
                RouteTextResolution.Unsupported
            } else RouteTextResolution.Unreadable
        }
    }

    fun patternFromHeadsign(routeId: String, text: String, catalog: RouteCatalog): Set<String> {
        val normalized = " ${normalizeText(text)} "
        return catalog.patterns.asSequence()
            .filter { it.routeId == routeId && it.active && it.verificationStatus == VerificationStatus.VERIFIED }
            .filter { pattern ->
                val headsign = normalizeText(pattern.branchHeadsign)
                headsign.isNotEmpty() && normalized.contains(" $headsign ")
            }
            .map { it.id }
            .toSet()
    }

    fun unverifiedPatternFromHeadsign(routeId: String, text: String, catalog: RouteCatalog): Boolean {
        val normalized = " ${normalizeText(text)} "
        return catalog.patterns.any { pattern ->
            pattern.routeId == routeId && (!pattern.active || pattern.verificationStatus != VerificationStatus.VERIFIED) &&
                normalized.contains(" ${normalizeText(pattern.branchHeadsign)} ")
        }
    }

    fun conflictsWithConfirmation(
        routeResolution: RouteTextResolution,
        patternIds: Set<String>,
        confirmedRouteId: String?,
        confirmedPatternId: String?,
        observedText: String,
        catalog: RouteCatalog,
    ): Boolean {
        if (confirmedRouteId == null || confirmedPatternId == null) return false
        val routeConflict = when (routeResolution) {
            is RouteTextResolution.Found -> routeResolution.routeId != confirmedRouteId
            is RouteTextResolution.Ambiguous, RouteTextResolution.Unsupported -> true
            RouteTextResolution.Unreadable -> false
        }
        return routeConflict || patternIds.any { it != confirmedPatternId } ||
            (patternIds.isEmpty() && unverifiedPatternFromHeadsign(confirmedRouteId, observedText, catalog))
    }

    fun decide(
        routeResolution: RouteTextResolution,
        patternIds: Set<String>,
        userConfirmedPatternId: String?,
        catalog: RouteCatalog,
        eligibleJourneys: List<JourneyMatch>,
        neutral: Boolean,
        observedText: String = "",
    ): ScannerDecision {
        val routeId = (routeResolution as? RouteTextResolution.Found)?.routeId
        if (routeResolution is RouteTextResolution.Ambiguous || patternIds.size > 1 ||
            (userConfirmedPatternId != null && (
                catalog.patterns.firstOrNull { it.id == userConfirmedPatternId }?.routeId != routeId ||
                    (patternIds.isNotEmpty() && userConfirmedPatternId !in patternIds)
                ))
        ) return ScannerDecision(DecisionKind.UNCERTAIN)
        if (routeResolution == RouteTextResolution.Unreadable) return ScannerDecision(DecisionKind.UNREADABLE)
        if (routeResolution == RouteTextResolution.Unsupported) return ScannerDecision(DecisionKind.UNSUPPORTED)
        if (routeId == null) return ScannerDecision(DecisionKind.UNCERTAIN)

        val route = catalog.routes.firstOrNull { it.id == routeId }
            ?: return ScannerDecision(DecisionKind.UNSUPPORTED)
        if (catalog.patterns.none { it.routeId == routeId && it.active && it.verificationStatus == VerificationStatus.VERIFIED }) {
            return ScannerDecision(DecisionKind.UNSUPPORTED, routeId, route.name)
        }
        if (patternIds.isEmpty() && unverifiedPatternFromHeadsign(routeId, observedText, catalog)) {
            return ScannerDecision(DecisionKind.UNSUPPORTED, routeId, route.name)
        }
        val patternId = patternIds.singleOrNull() ?: userConfirmedPatternId
        val pattern = patternId?.let { id -> catalog.patterns.firstOrNull { it.id == id && it.routeId == routeId } }
        if (patternId != null && (pattern == null || !pattern.active || pattern.verificationStatus != VerificationStatus.VERIFIED)) {
            return ScannerDecision(DecisionKind.UNSUPPORTED, routeId, route.name)
        }
        if (neutral) return ScannerDecision(DecisionKind.IDENTIFIED, routeId, route.name, patternId, pattern?.direction)
        if (pattern == null) return ScannerDecision(DecisionKind.CONFIRM_DIRECTION, routeId, route.name)

        val source = if (patternIds.singleOrNull() != null) DirectionSource.SIGN else DirectionSource.USER
        val kind = if (eligibleJourneys.any { it.routeId == routeId && it.patternId == pattern.id }) {
            DecisionKind.MATCH
        } else DecisionKind.NO_MATCH
        return ScannerDecision(kind, routeId, route.name, pattern.id, pattern.direction, source)
    }

    private fun routeNumbers(aliases: List<String>): Set<String> = aliases.flatMap { alias ->
        Regex("(?i)\\broute\\s+(\\d+)\\b").findAll(alias).map { it.groupValues[1] }.toList()
    }.toSet()

    private fun normalizeText(value: String): String = normalizeAlias(value.lowercase(Locale.ROOT))
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

data class ScannerSettings(
    val version: String = "scanner-v1",
    val agreementObservations: Int = 2,
    val agreementWindowMs: Long = 1_000,
    val freshnessMs: Long = 1_000,
)

class TemporalDecisionTracker(private val settings: ScannerSettings = ScannerSettings()) {
    private var session = 0L
    private var contextKey = ""
    private var targetId: String? = null
    private var pendingSignature: String? = null
    private var count = 0
    private var firstCaptureMs = 0L
    private var lastCaptureMs = 0L
    private var decision: ScannerDecision? = null

    fun beginSession(context: String): Long {
        session++
        contextKey = context
        targetId = null
        resetVotes()
        return session
    }

    fun updateContext(context: String): Long {
        if (context != contextKey) {
            session++
            contextKey = context
            targetId = null
            resetVotes()
        }
        return session
    }

    fun selectTarget(id: String?) {
        if (id != targetId) {
            targetId = id
            resetVotes()
        }
    }

    fun acceptsCallback(callbackSession: Long, callbackContext: String, callbackTarget: String): Boolean =
        callbackSession == session && callbackContext == contextKey && callbackTarget == targetId

    fun observe(
        callbackSession: Long,
        callbackContext: String,
        callbackTarget: String,
        capturedAtMs: Long,
        observed: ScannerDecision,
    ): Boolean {
        if (!acceptsCallback(callbackSession, callbackContext, callbackTarget) || capturedAtMs <= lastCaptureMs) return false
        if (decision != null && pendingSignature == observed.signature && capturedAtMs - lastCaptureMs <= settings.freshnessMs) {
            lastCaptureMs = capturedAtMs
            decision = observed
            return true
        }
        if (decision != null && capturedAtMs - lastCaptureMs > settings.freshnessMs) resetVotes()
        if (pendingSignature != observed.signature || capturedAtMs - firstCaptureMs > settings.agreementWindowMs) {
            pendingSignature = observed.signature
            count = 1
            firstCaptureMs = capturedAtMs
            decision = null
        } else count++
        lastCaptureMs = capturedAtMs
        if (count >= settings.agreementObservations) decision = observed
        return decision != null
    }

    fun current(nowMs: Long): ScannerDecision? {
        if (decision != null && nowMs - lastCaptureMs > settings.freshnessMs) resetVotes()
        return decision
    }

    fun invalidate() {
        resetVotes()
    }

    private fun resetVotes() {
        pendingSignature = null
        count = 0
        firstCaptureMs = 0
        lastCaptureMs = 0
        decision = null
    }
}
