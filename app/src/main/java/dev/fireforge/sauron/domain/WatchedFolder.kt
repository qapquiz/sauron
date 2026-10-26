package dev.fireforge.sauron.domain

enum class WatchedFolder(val relativePath: String, val bank: Bank) {
    CLICX("Pictures/CLICX", Bank.CLICX),
    K_PLUS("Pictures/K PLUS", Bank.KBANK),
    SCB_EASY("Pictures/SCB EASY", Bank.SCB),
    ;

    companion object {

        fun selection(
            relativePathColumn: String,
            dataColumn: String,
            dateAddedColumn: String,
            useRelativePath: Boolean,
            watermarkSeconds: Long,
        ): Pair<String, Array<String>> {
            val clauses = entries.flatMap { exactOrSubtreeClauses(it, relativePathColumn, dataColumn, useRelativePath) }
            val sql = "(${clauses.joinToString(" OR ") { it.first }}) AND $dateAddedColumn >= ?"
            val args = (clauses.map { it.second } + watermarkSeconds.toString()).toTypedArray()
            return sql to args
        }

        private fun exactOrSubtreeClauses(
            folder: WatchedFolder,
            relativePathColumn: String,
            dataColumn: String,
            useRelativePath: Boolean,
        ): List<Pair<String, String>> =
            if (useRelativePath) {
                listOf(
                    "$relativePathColumn = ?" to "${folder.relativePath}/",
                    "$relativePathColumn LIKE ?" to "${folder.relativePath}/%",
                )
            } else {
                listOf("$dataColumn LIKE ?" to "%/${folder.relativePath}/%")
            }
    }
}
