package at.se2_ss2025_gruppec.carcasonnefrontend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

//Import Libraries for testing

class MainActivityTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
    /*
    @Test
    fun `tile with all sides same color`() {
        val tile = Tile(
            top = Color.Blue,
            right = Color.Blue,
            bottom = Color.Blue,
            left = Color.Blue
        )

        assertEquals(Color.Blue, tile.top)
        assertEquals(Color.Blue, tile.right)
        assertEquals(Color.Blue, tile.bottom)
        assertEquals(Color.Blue, tile.left)
    }

    @Test
    fun `tile with symmetrical sides`() {
        val tile = Tile(
            top = Color.Red,
            right = Color.Green,
            bottom = Color.Red,
            left = Color.Green
        )

        assertEquals(tile.top, tile.bottom)
        assertEquals(tile.right, tile.left)
    }

    @Test
    fun `rotating tile 4 times returns to original`() {
        val original = Tile(
            top = Color.Red,
            right = Color.Green,
            bottom = Color.Blue,
            left = Color.Yellow
        )

        val rotated4 = original.rotated().rotated().rotated().rotated()

        assertEquals(original, rotated4)
    }
    @Test
    fun `tiles with different colors are not equal`() {
        val tile1 = Tile(Color.Red, Color.Green, Color.Blue, Color.Yellow)
        val tile2 = Tile(Color.Red, Color.Green, Color.Blue, Color.Green)

        assert(tile1 != tile2)
    }
        Diese beiden Tests nur hinzufügen wenn passend zu Semantik



    @Test
    fun `rotating tile with same colors on all sides still returns equal tile`() {
        val tile = Tile(Color.Red, Color.Red, Color.Red, Color.Red)
        val rotated = tile.rotated()

        assertEquals(tile, rotated)
    }

    @Test
    fun `symmetrical tile equals after two rotations`() {
        val tile = Tile(Color.Red, Color.Green, Color.Red, Color.Green)
        val rotatedTwice = tile.rotated().rotated()

        assertEquals(tile, rotatedTwice)
    }
*/

}