package com.catsmoker.app.screens.gaming.display

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ELF/symbol reader that discovers Samsung's pacesetter transaction numbers.
 *
 * Tested against a hand-built ELF64 object rather than a system library so the assertions are
 * about the parsing rules: a symbol is found through the dynamic symbol table and the load
 * segment, exactly one `MOVZ w1, #imm` is decoded, and anything ambiguous is refused. The
 * refusal cases matter more than the success case — a wrong transaction number would be sent to
 * an unrelated SurfaceFlinger method.
 */
class AArch64MovzTest {

    private companion object {
        const val EHDR = 64
        const val PHDR = 56
        const val SHDR = 64
        const val SYM = 24

        const val PHDR_OFFSET = EHDR
        const val STRTAB_OFFSET = 0x80
        const val CODE_OFFSET = 0x100
        const val DYNSYM_OFFSET = 0x140
        const val SHDR_OFFSET = 0x190

        const val FORCE = "forcePacesetter"
        const val RESET = "resetForcedPacesetter"

        /** `MOVZ w1, #imm` — the load the AIDL proxy performs before `transact()`. */
        fun movzW1(imm: Int): Int = 0x52800000.toInt() or (imm shl 5) or 1
        const val RET = 0xD65F03C0.toInt()
    }

    private fun build(
        code: IntArray,
        forceCodeAt: Int = 0,
        resetCodeAt: Int = 1,
        extraMovzInForce: Int? = null
    ): ByteBuffer {
        val strtab = ByteArray(64)
        var cursor = 1
        val offsets = HashMap<String, Int>()
        for (name in listOf(FORCE, RESET)) {
            offsets[name] = cursor
            for (byte in name.toByteArray(Charsets.US_ASCII)) strtab[cursor++] = byte
            strtab[cursor++] = 0
        }

        val buffer = ByteBuffer.allocate(0x400).order(ByteOrder.LITTLE_ENDIAN)
        // ELF header.
        buffer.putInt(0x00, 0x464C457F)
        buffer.put(4, 2.toByte()); buffer.put(5, 1.toByte()); buffer.put(6, 1.toByte())
        buffer.putShort(0x10, 3)                      // ET_DYN
        buffer.putShort(0x12, 183)                    // EM_AARCH64
        buffer.putLong(0x20, PHDR_OFFSET.toLong())    // e_phoff
        buffer.putLong(0x28, SHDR_OFFSET.toLong())    // e_shoff
        buffer.putShort(0x34, EHDR.toShort())
        buffer.putShort(0x36, PHDR.toShort())
        buffer.putShort(0x38, 1)
        buffer.putShort(0x3A, SHDR.toShort())
        buffer.putShort(0x3C, 3)

        // One PT_LOAD mapping CODE_OFFSET..CODE_OFFSET+code bytes.
        val codeSize = code.size * 4
        buffer.putInt(PHDR_OFFSET, 1)
        buffer.putLong(PHDR_OFFSET + 0x08, CODE_OFFSET.toLong())
        buffer.putLong(PHDR_OFFSET + 0x10, CODE_OFFSET.toLong())
        buffer.putLong(PHDR_OFFSET + 0x20, codeSize.toLong())
        buffer.putLong(PHDR_OFFSET + 0x28, codeSize.toLong())

        for (index in strtab.indices) buffer.put(STRTAB_OFFSET + index, strtab[index])
        for (index in code.indices) buffer.putInt(CODE_OFFSET + index * 4, code[index])

        // Symbol 0 is the mandatory null entry.
        buffer.putLong(DYNSYM_OFFSET.toLong().toInt(), 0L)
        writeSymbol(buffer, 1, offsets[FORCE]!!, CODE_OFFSET + forceCodeAt * 4, 8)
        writeSymbol(buffer, 2, offsets[RESET]!!, CODE_OFFSET + resetCodeAt * 4, 8)

        // Section 0: SHT_NULL. Section 1: SHT_DYNSYM linked to section 2. Section 2: SHT_STRTAB.
        buffer.putInt(SHDR_OFFSET + SHDR + 4, 11)
        buffer.putLong(SHDR_OFFSET + SHDR + 0x18, DYNSYM_OFFSET.toLong())
        buffer.putLong(SHDR_OFFSET + SHDR + 0x20, (3 * SYM).toLong())
        buffer.putInt(SHDR_OFFSET + SHDR + 0x28, 2)
        buffer.putInt(SHDR_OFFSET + 2 * SHDR + 4, 3)
        buffer.putLong(SHDR_OFFSET + 2 * SHDR + 0x18, STRTAB_OFFSET.toLong())
        return buffer
    }

    private fun writeSymbol(buffer: ByteBuffer, index: Int, name: Int, value: Int, size: Int) {
        val base = DYNSYM_OFFSET + index * SYM
        buffer.putInt(base, name)
        buffer.put(base + 4, 0x12.toByte())           // STB_GLOBAL / STT_FUNC
        buffer.putShort(base + 6, 1)
        buffer.putLong(base + 8, value.toLong())
        buffer.putLong(base + 16, size.toLong())
    }

    @Test
    fun decodesTheTransactionCodeFromTheProxyStub() {
        val data = build(code = intArrayOf(movzW1(35), RET, movzW1(36), RET))
        val force = AArch64Movz.findFunction(data, FORCE)
        val reset = AArch64Movz.findFunction(data, RESET)
        assertEquals(CODE_OFFSET, force?.fileOffset)
        assertEquals(35, AArch64Movz.uniqueTransactionCode(data, force!!))
        assertEquals(36, AArch64Movz.uniqueTransactionCode(data, reset!!))
    }

    @Test
    fun refusesAStubThatLoadsTwoDifferentCodes() {
        // A branchy stub cannot be read unambiguously, and guessing would call another method.
        val data = build(code = intArrayOf(movzW1(35), movzW1(36), RET, movzW1(37), RET))
        val force = AArch64Movz.findFunction(data, FORCE)
        assertNull(AArch64Movz.uniqueTransactionCode(data, force!!))
    }

    @Test
    fun refusesAStubWithNoCodeLoad() {
        val data = build(code = intArrayOf(RET, 0, RET, 0))
        val force = AArch64Movz.findFunction(data, FORCE)
        assertNull(AArch64Movz.uniqueTransactionCode(data, force!!))
    }

    @Test
    fun ignoresCodesLoadedIntoOtherRegisters() {
        // `MOVZ w0, #77` is not the transaction code; the AIDL proxy uses w1.
        val notW1 = (0x52800000.toInt() or (77 shl 5)) // rd = 0
        val data = build(code = intArrayOf(notW1, RET, movzW1(36), RET))
        val force = AArch64Movz.findFunction(data, FORCE)
        assertNull(AArch64Movz.uniqueTransactionCode(data, force!!))
    }

    @Test
    fun reportsMissingSymbolsAndNonElfDataAsAbsent() {
        val data = build(code = intArrayOf(movzW1(35), RET))
        assertNull(AArch64Movz.findFunction(data, "noSuchSymbol"))
        assertNull(AArch64Movz.findFunction(ByteBuffer.allocate(128), FORCE))
    }
}
