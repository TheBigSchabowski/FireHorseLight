package repertoire

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PieceImagesTest {
    /** An SVG without width/height once rendered to an empty image and the kings vanished from the board. */
    @Test
    fun everyPieceImageHasContent() {
        val dir = File("src/commonMain/composeResources/drawable")
        for (color in "wb") for (piece in "kqrbnp") {
            val file = File(dir, "$color$piece.png")
            assertTrue(file.length() > 1000, "${file.name} is missing or empty (${file.length()} bytes)")
        }
    }
}
