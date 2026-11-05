package dev.fireforge.sauron.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchedFolderTest {

    @Test
    fun relativePathSelectionMatchesExactFolderOrSubtree() {
        val (sql, args) = WatchedFolder.selection(
            relativePathColumn = "relative_path",
            dataColumn = "_data",
            dateAddedColumn = "date_added",
            useRelativePath = true,
            watermarkSeconds = 1728600000L,
        )
        assertEquals(
            "(" +
                "relative_path = ? OR relative_path LIKE ? OR " +
                "relative_path = ? OR relative_path LIKE ? OR " +
                "relative_path = ? OR relative_path LIKE ?" +
                ") AND date_added >= ?",
            sql,
        )
        assertArrayEquals(
            arrayOf(
                "Pictures/CLICX/",
                "Pictures/CLICX/%",
                "Pictures/K PLUS/",
                "Pictures/K PLUS/%",
                "Pictures/SCB EASY/",
                "Pictures/SCB EASY/%",
                "1728600000",
            ),
            args,
        )
    }

    @Test
    fun preQSelectionMatchesByDataPath() {
        val (sql, args) = WatchedFolder.selection(
            relativePathColumn = "relative_path",
            dataColumn = "_data",
            dateAddedColumn = "date_added",
            useRelativePath = false,
            watermarkSeconds = 1728600000L,
        )
        assertEquals(
            "(_data LIKE ? OR _data LIKE ? OR _data LIKE ?) AND date_added >= ?",
            sql,
        )
        assertArrayEquals(
            arrayOf(
                "%/Pictures/CLICX/%",
                "%/Pictures/K PLUS/%",
                "%/Pictures/SCB EASY/%",
                "1728600000",
            ),
            args,
        )
    }
}
