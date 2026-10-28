package com.dosprint.domain.model

/**
 * The paper sizes offered for the current printer, split by whether the printer can actually do
 * them.
 *
 * Showing one flat list of every known size is misleading: picking a size the printer cannot load
 * looks like it worked, and the request is quietly ignored. Splitting the catalogue into what the
 * printer reports and what it does not means the choice is honest, and the common sizes stay
 * reachable for a printer whose capabilities are not yet known.
 */
data class PaperChoices(
    val supported: List<PaperSize>,
    val other: List<PaperSize>,
    val isPrinterKnown: Boolean,
) {
    val all: List<PaperSize> = supported + other

    val isEmpty: Boolean get() = all.isEmpty()

    /** True when the printer reports its supported sizes, so the split can be trusted. */
    val isReliable: Boolean get() = isPrinterKnown && supported.isNotEmpty()

    fun contains(id: String): Boolean = all.any { it.id == id }

    companion object {
        /**
         * Builds the choices for [capabilities].
         *
         * When the printer does not report its sizes the whole catalogue is offered as supported,
         * because claiming none of them work would be a worse lie than offering them all.
         */
        fun forPrinter(
            capabilities: PrinterCapabilities,
            selected: PaperSize,
        ): PaperChoices {
            val reported = capabilities.supportedPaperSizes.filter { it.widthMils > 0 && it.heightMils > 0 }
            if (capabilities.unknown || reported.isEmpty()) {
                return PaperChoices(
                    supported = (PaperSize.COMMON + selected).distinctById(),
                    other = emptyList(),
                    isPrinterKnown = false,
                )
            }
            val keepSelected = listOfNotNull(
                selected.takeIf { candidate -> reported.none { it.matches(candidate) } },
            )
            val supported = (reported + keepSelected).distinctById()
            val other = PaperSize.ALL.filterNot { size -> supported.any { it.matches(size) } }
            return PaperChoices(supported = supported, other = other, isPrinterKnown = true)
        }

        private fun List<PaperSize>.distinctById(): List<PaperSize> {
            val seen = HashSet<String>(size)
            return filter { seen.add(it.id) }
        }
    }
}
