package com.catsmoker.app.screens.gaming.display

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads a `MOVZ Wd, #imm` immediate out of a function in an ELF64 AArch64 shared library.
 *
 * Why this exists: Samsung appends `forcePacesetter`/`resetForcedPacesetter` to its
 * `android.gui.ISurfaceComposer`, which gives them transaction codes that shift when the
 * vendor branch changes. Hardcoding a code would silently call a *different* method after
 * a firmware update, so the code is read from the client stub the platform actually ships:
 * the AIDL proxy loads its transaction number into `w1` immediately before `transact()`.
 *
 * Implemented from the ELF64 and AArch64 encoding specifications, independently of any
 * project that reads the same constants. Nothing is written and no code is executed — this
 * is a read-only parse of a system library.
 */
object AArch64Movz {

    private const val ELF_MAGIC = 0x464C457F
    private const val ELFCLASS64 = 2
    private const val ELFDATA2LSB = 1
    private const val ET_DYN = 3
    private const val EM_AARCH64 = 183

    private const val SHT_DYNSYM = 11
    private const val PT_LOAD = 1

    private const val SYM_ENTSIZE = 24
    /** 64-bit `MOVZ Wd, #imm16, LSL #0` with sf=0 and opc=0b10. */
    private const val MOVZ_W_BASE = 0x52800000.toInt()
    private const val MOVZ_W_MASK = 0xFF800000.toInt()
    /** Register numbers and the accumulator the AIDL stub uses for the transaction code. */
    private const val TRANSACT_CODE_REG = 1

    /**
     * Where a named exported function lives in the file.
     *
     * @param fileOffset byte offset of the first instruction inside [data].
     * @param size the function's declared size, or 0 when the symbol carries none.
     */
    data class FunctionLocation(val fileOffset: Int, val size: Int)

    /**
     * The single `MOVZ w1, #imm` immediate in [function], or null when the function has
     * zero or more than one — a shape this decoder does not trust.
     *
     * Requiring exactly one is the safety property: a stub that sets `w1` in a branch (or
     * twice) cannot be read unambiguously, and guessing would send a real transaction to
     * the wrong method.
     */
    fun uniqueTransactionCode(data: ByteBuffer, location: FunctionLocation): Int? {
        val window = instructionWindow(location)
        val codes = HashSet<Int>()
        var offset = window.first
        while (offset + 4 <= window.last + 1) {
            val instruction = data.getInt(offset)
            if ((instruction and MOVZ_W_MASK) == MOVZ_W_BASE &&
                (instruction and 0x1F) == TRANSACT_CODE_REG &&
                ((instruction ushr 21) and 0x3) == 0
            ) {
                codes += (instruction ushr 5) and 0xFFFF
            }
            offset += 4
        }
        return codes.singleOrNull()?.takeIf { it > 0 }
    }

    /**
     * Parses [data] and locates [symbol].
     *
     * @return null when the file is not an AArch64 ELF64 shared object, has no dynamic
     *   symbol table, or does not export [symbol]. Every one of those is "this firmware
     *   does not have the call", which is a capability answer rather than an error.
     */
    fun findFunction(data: ByteBuffer, symbol: String): FunctionLocation? =
        findFunctionMatching(data) { it == symbol }

    /**
     * Parses [data] and locates the first exported function whose name satisfies [match].
     *
     * Substring matching rather than an exact mangled name: a vendor branch is free to
     * change the parameter type of an argument it does not use, which changes the mangling
     * while leaving the transaction code a plain constant load.
     */
    fun findFunctionMatching(data: ByteBuffer, match: (String) -> Boolean): FunctionLocation? {
        data.order(ByteOrder.LITTLE_ENDIAN)
        if (data.capacity() < 64) return null
        if (data.getInt(0) != ELF_MAGIC || data.get(4) != ELFCLASS64.toByte()) return null
        if (data.get(5) != ELFDATA2LSB.toByte()) return null
        val type = data.getShort(0x10).toInt() and 0xFFFF
        if (type != ET_DYN) return null
        if ((data.getShort(0x12).toInt() and 0xFFFF) != EM_AARCH64) return null

        val sectionHeaderOffset = data.getLong(0x28)
        val sectionEntrySize = data.getShort(0x3A).toInt() and 0xFFFF
        val sectionCount = data.getShort(0x3C).toInt() and 0xFFFF
        if (sectionHeaderOffset <= 0 || sectionEntrySize < 64 || sectionCount <= 0) return null

        var symbolTableOffset = -1L
        var symbolTableSize = 0L
        var stringTableOffset = -1L
        for (index in 0 until sectionCount) {
            val base = sectionHeaderOffset + index.toLong() * sectionEntrySize
            if (!fits(data, base, 64)) return null
            if (data.getInt(base.toInt() + 4) != SHT_DYNSYM) continue
            symbolTableOffset = data.getLong((base + 0x18).toInt())
            symbolTableSize = data.getLong((base + 0x20).toInt())
            val link = data.getInt((base + 0x28).toInt())
            val linkedHeader = sectionHeaderOffset + link.toLong() * sectionEntrySize
            if (!fits(data, linkedHeader, 64)) return null
            stringTableOffset = data.getLong((linkedHeader + 0x18).toInt())
        }
        if (symbolTableOffset < 0 || stringTableOffset < 0) return null

        val entryCount = (symbolTableSize / SYM_ENTSIZE).toInt()
        for (index in 0 until entryCount) {
            val entry = symbolTableOffset + index.toLong() * SYM_ENTSIZE
            if (!fits(data, entry, SYM_ENTSIZE)) return null
            val nameOffset = data.getInt(entry.toInt()).toLong() and 0xFFFFFFFFL
            val name = readCString(data, stringTableOffset + nameOffset) ?: continue
            if (!match(name)) continue
            val value = data.getLong((entry + 8).toInt())
            val size = data.getLong((entry + 16).toInt())
            val fileOffset = virtualToFileOffset(data, value) ?: continue
            if (fileOffset < 0) continue
            return FunctionLocation(fileOffset.toInt(), size.coerceIn(0, MAX_FUNCTION_BYTES.toLong()).toInt())
        }
        return null
    }

    /** Maps a symbol's virtual address to a file offset through the `PT_LOAD` segments. */
    private fun virtualToFileOffset(data: ByteBuffer, address: Long): Long? {
        val programHeaderOffset = data.getLong(0x20)
        val programEntrySize = data.getShort(0x36).toInt() and 0xFFFF
        val programCount = data.getShort(0x38).toInt() and 0xFFFF
        if (programHeaderOffset <= 0 || programEntrySize < 56) return null
        for (index in 0 until programCount) {
            val base = programHeaderOffset + index.toLong() * programEntrySize
            if (!fits(data, base, 56)) return null
            if (data.getInt(base.toInt()) != PT_LOAD) continue
            val offset = data.getLong((base + 0x08).toInt())
            val vaddr = data.getLong((base + 0x10).toInt())
            val fileSize = data.getLong((base + 0x20).toInt())
            if (address >= vaddr && address < vaddr + fileSize) {
                return offset + (address - vaddr)
            }
        }
        return null
    }

    /**
     * The byte range to scan: the declared function size, or a bounded window when the
     * symbol carries none (assembly stubs frequently declare `st_size == 0`).
     */
    private fun instructionWindow(location: FunctionLocation): IntRange {
        val size = if (location.size >= 8) location.size.coerceAtMost(MAX_FUNCTION_BYTES)
        else UNKNOWN_SIZE_WINDOW
        return location.fileOffset until (location.fileOffset + size)
    }

    private fun fits(data: ByteBuffer, position: Long, length: Int = 8): Boolean =
        position >= 0 && position + length <= data.capacity()

    /** Reads a NUL-terminated name, or null when [offset] is outside the file. */
    private fun readCString(data: ByteBuffer, offset: Long): String? {
        if (offset < 0 || offset >= data.capacity()) return null
        val builder = StringBuilder(64)
        var cursor = offset.toInt()
        while (cursor < data.capacity()) {
            val byte = data.get(cursor)
            if (byte.toInt() == 0) return builder.toString()
            builder.append((byte.toInt() and 0xFF).toChar())
            cursor++
            // A symbol name this long is not a symbol name; stop instead of reading the table.
            if (builder.length > MAX_NAME_CHARS) return null
        }
        return null
    }

    /** Longest symbol name considered; the vendor stubs are far shorter. */
    private const val MAX_NAME_CHARS = 512

    /** A stub longer than this is not a stub; the cap keeps a bad `st_size` harmless. */
    private const val MAX_FUNCTION_BYTES = 4096

    /** How far past an unsized symbol to look for its `MOVZ`. */
    private const val UNKNOWN_SIZE_WINDOW = 256
}
