/*
  Stockfish, a UCI chess playing engine derived from Glaurung 2.1
  Copyright (C) 2004-2008 Tord Romstad (Glaurung author)
  Copyright (C) 2008-2012 Marco Costalba, Joona Kiiski, Tord Romstad

  Stockfish is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  Stockfish is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

#if !defined(TYPES_H_INCLUDED)
#define TYPES_H_INCLUDED

/// For Linux and OSX configuration is done automatically using Makefile. To get
/// started type 'make help'.
///
/// For Windows, part of the configuration is detected automatically, but some
/// switches need to be set manually:
///
/// -DNDEBUG      | Disable debugging mode. Use always.
///
/// -DNO_PREFETCH | Disable use of prefetch asm-instruction. A must if you want
///               | the executable to run on some very old machines.
///
/// -DUSE_POPCNT  | Add runtime support for use of popcnt asm-instruction. Works
///               | only in 64-bit mode. For compiling requires hardware with
///               | popcnt support.
///
/// -DOLD_LOCKS   | Under Windows are used the fast Slim Reader/Writer (SRW)
///               | Locks and Condition Variables: these are not supported by
///               | Windows XP and older, to compile for those platforms you
///               | should enable OLD_LOCKS.

#include <climits>
#include <cstdlib>

#if defined(_MSC_VER)

// Disable some silly and noisy warning from MSVC compiler
#pragma warning(disable: 4127) // Conditional expression is constant
#pragma warning(disable: 4146) // Unary minus operator applied to unsigned type
#pragma warning(disable: 4800) // Forcing value to bool 'true' or 'false'

// MSVC does not support <inttypes.h>
typedef   signed __int8    int8_t;
typedef unsigned __int8   uint8_t;
typedef   signed __int16  int16_t;
typedef unsigned __int16 uint16_t;
typedef   signed __int32  int32_t;
typedef unsigned __int32 uint32_t;
typedef   signed __int64  int64_t;
typedef unsigned __int64 uint64_t;

#else
#  include <inttypes.h>
#endif

#if defined(_WIN64)
#  include <intrin.h> // MSVC popcnt and bsfq instrinsics
#  define IS_64BIT
#  define USE_BSFQ
#endif

#if defined(USE_POPCNT) && defined(_MSC_VER) && defined(__INTEL_COMPILER)
#  include <nmmintrin.h> // Intel header for _mm_popcnt_u64() intrinsic
#endif

#if defined(_MSC_VER) || defined(__INTEL_COMPILER)
#  define CACHE_LINE_ALIGNMENT __declspec(align(64))
#else
#  define CACHE_LINE_ALIGNMENT  __attribute__ ((aligned(64)))
#endif

#if defined(_MSC_VER)
#  define FORCE_INLINE  __forceinline
#elif defined(__GNUC__)
#  define FORCE_INLINE  inline __attribute__((always_inline))
#else
#  define FORCE_INLINE  inline
#endif

#if defined(USE_POPCNT)
const bool HasPopCnt = true;
#else
const bool HasPopCnt = false;
#endif

#if defined(IS_64BIT)
const bool Is64Bit = true;
#else
const bool Is64Bit = false;
#endif

typedef uint64_t Key;
typedef uint64_t Bitboard;

const int MAX_MOVES      = 256;
const int MAX_PLY        = 100;
const int MAX_PLY_PLUS_2 = MAX_PLY + 2;

const Bitboard FileABB = 0x0101010101010101ULL;
const Bitboard FileBBB = FileABB << 1;
const Bitboard FileCBB = FileABB << 2;
const Bitboard FileDBB = FileABB << 3;
const Bitboard FileEBB = FileABB << 4;
const Bitboard FileFBB = FileABB << 5;
const Bitboard FileGBB = FileABB << 6;
const Bitboard FileHBB = FileABB << 7;

const Bitboard Rank1BB = 0xFF;
const Bitboard Rank2BB = Rank1BB << (8 * 1);
const Bitboard Rank3BB = Rank1BB << (8 * 2);
const Bitboard Rank4BB = Rank1BB << (8 * 3);
const Bitboard Rank5BB = Rank1BB << (8 * 4);
const Bitboard Rank6BB = Rank1BB << (8 * 5);
const Bitboard Rank7BB = Rank1BB << (8 * 6);
const Bitboard Rank8BB = Rank1BB << (8 * 7);


/// A move needs 16 bits to be stored
///
/// bit  0- 5: destination square (from 0 to 63)
/// bit  6-11: origin square (from 0 to 63)
/// bit 12-13: promotion piece type - 2 (from KNIGHT-2 to QUEEN-2)
/// bit 14-15: special move flag: promotion (1), en passant (2), castle (3)
///
/// Special cases are MOVE_NONE and MOVE_NULL. We can sneak these in because in
/// any normal move destination square is always different from origin square
/// while MOVE_NONE and MOVE_NULL have the same origin and destination square.

enum Move {
  MOVE_NONE = 0,
  MOVE_NULL = 65
};

struct MoveStack {
  Move move;
  int score;
};

inline bool operator<(const MoveStack& f, const MoveStack& s) {
  return f.score < s.score;
}

enum CastleRight {
  CASTLES_NONE = 0,
  WHITE_OO     = 1,
  BLACK_OO     = 2,
  WHITE_OOO    = 4,
  BLACK_OOO    = 8,
  ALL_CASTLES  = 15
};

enum ScaleFactor {
  SCALE_FACTOR_DRAW   = 0,
  SCALE_FACTOR_NORMAL = 64,
  SCALE_FACTOR_MAX    = 128,
  SCALE_FACTOR_NONE   = 255
};

enum ValueType {
  VALUE_TYPE_NONE  = 0,
  VALUE_TYPE_UPPER = 1,
  VALUE_TYPE_LOWER = 2,
  VALUE_TYPE_EXACT = VALUE_TYPE_UPPER | VALUE_TYPE_LOWER
};

enum Value {
  VALUE_ZERO      = 0,
  VALUE_DRAW      = 0,
  VALUE_KNOWN_WIN = 15000,
  VALUE_MATE      = 30000,
  VALUE_INFINITE  = 30001,
  VALUE_NONE      = 30002,

  VALUE_MATE_IN_MAX_PLY  =  VALUE_MATE - MAX_PLY,
  VALUE_MATED_IN_MAX_PLY = -VALUE_MATE + MAX_PLY,

  VALUE_ENSURE_INTEGER_SIZE_P = INT_MAX,
  VALUE_ENSURE_INTEGER_SIZE_N = INT_MIN
};

enum PieceType {
  NO_PIECE_TYPE = 0,
  PAWN = 1, KNIGHT = 2, BISHOP = 3, ROOK = 4, QUEEN = 5, KING = 6
};

enum Piece {
  NO_PIECE = 16, // color_of(NO_PIECE) == NO_COLOR
  W_PAWN = 1, W_KNIGHT =  2, W_BISHOP =  3, W_ROOK =  4, W_QUEEN =  5, W_KING =  6,
  B_PAWN = 9, B_KNIGHT = 10, B_BISHOP = 11, B_ROOK = 12, B_QUEEN = 13, B_KING = 14
};

enum Color {
  WHITE, BLACK, NO_COLOR
};

enum Depth {

  ONE_PLY = 2,

  DEPTH_ZERO          =  0 * ONE_PLY,
  DEPTH_QS_CHECKS     = -1 * ONE_PLY,
  DEPTH_QS_NO_CHECKS  = -2 * ONE_PLY,
  DEPTH_QS_RECAPTURES = -4 * ONE_PLY,

  DEPTH_NONE = -127 * ONE_PLY
};

enum Square {
  SQ_A1, SQ_B1, SQ_C1, SQ_D1, SQ_E1, SQ_F1, SQ_G1, SQ_H1,
  SQ_A2, SQ_B2, SQ_C2, SQ_D2, SQ_E2, SQ_F2, SQ_G2, SQ_H2,
  SQ_A3, SQ_B3, SQ_C3, SQ_D3, SQ_E3, SQ_F3, SQ_G3, SQ_H3,
  SQ_A4, SQ_B4, SQ_C4, SQ_D4, SQ_E4, SQ_F4, SQ_G4, SQ_H4,
  SQ_A5, SQ_B5, SQ_C5, SQ_D5, SQ_E5, SQ_F5, SQ_G5, SQ_H5,
  SQ_A6, SQ_B6, SQ_C6, SQ_D6, SQ_E6, SQ_F6, SQ_G6, SQ_H6,
  SQ_A7, SQ_B7, SQ_C7, SQ_D7, SQ_E7, SQ_F7, SQ_G7, SQ_H7,
  SQ_A8, SQ_B8, SQ_C8, SQ_D8, SQ_E8, SQ_F8, SQ_G8, SQ_H8,
  SQ_NONE,

  DELTA_N =  8,
  DELTA_E =  1,
  DELTA_S = -8,
  DELTA_W = -1,

  DELTA_NN = DELTA_N + DELTA_N,
  DELTA_NE = DELTA_N + DELTA_E,
  DELTA_SE = DELTA_S + DELTA_E,
  DELTA_SS = DELTA_S + DELTA_S,
  DELTA_SW = DELTA_S + DELTA_W,
  DELTA_NW = DELTA_N + DELTA_W
};

enum File {
  FILE_A, FILE_B, FILE_C, FILE_D, FILE_E, FILE_F, FILE_G, FILE_H
};

enum Rank {
  RANK_1, RANK_2, RANK_3, RANK_4, RANK_5, RANK_6, RANK_7, RANK_8
};


/// Score enum keeps a midgame and an endgame value in a single integer (enum),
/// first LSB 16 bits are used to store endgame value, while upper bits are used
/// for midgame value. Compiler is free to choose the enum type as long as can
/// keep its data, so ensure Score to be an integer type.
enum Score {
  SCORE_ZERO = 0,
  SCORE_ENSURE_INTEGER_SIZE_P = INT_MAX,
  SCORE_ENSURE_INTEGER_SIZE_N = INT_MIN
};

inline Score make_score(int mg, int eg) { return Score((mg << 16) + eg); }

/// Extracting the signed lower and upper 16 bits it not so trivial because
/// according to the standard a simple cast to short is implementation defined
/// and so is a right shift of a signed integer.
inline Value mg_value(Score s) { return Value(((s + 32768) & ~0xffff) / 0x10000); }

/// On Intel 64 bit we have a small speed regression with the standard conforming
/// version, so use a faster code in this case that, although not 100% standard
/// compliant it seems to work for Intel and MSVC.
#if defined(IS_64BIT) && (!defined(__GNUC__) || defined(__INTEL_COMPILER))

inline Value eg_value(Score s) { return Value(int16_t(s & 0xffff)); }

#else

inline Value eg_value(Score s) {
  return Value((int)(unsigned(s) & 0x7fffu) - (int)(unsigned(s) & 0x8000u));
}

#endif

#define ENABLE_SAFE_OPERATORS_ON(T)                                         \
inline T operator+(const T d1, const T d2) { return T(int(d1) + int(d2)); } \
inline T operator-(const T d1, const T d2) { return T(int(d1) - int(d2)); } \
inline T operator*(int i, const T d) { return T(i * int(d)); }              \
inline T operator*(const T d, int i) { return T(int(d) * i); }              \
inline T operator-(const T d) { return T(-int(d)); }                        \
inline T& operator+=(T& d1, const T d2) { d1 = d1 + d2; return d1; }        \
inline T& operator-=(T& d1, const T d2) { d1 = d1 - d2; return d1; }        \
inline T& operator*=(T& d, int i) { d = T(int(d) * i); return d; }

#define ENABLE_OPERATORS_ON(T) ENABLE_SAFE_OPERATORS_ON(T)                  \
inline T operator++(T& d, int) { d = T(int(d) + 1); return d; }             \
inline T operator--(T& d, int) { d = T(int(d) - 1); return d; }             \
inline T operator/(const T d, int i) { return T(int(d) / i); }              \
inline T& operator/=(T& d, int i) { d = T(int(d) / i); return d; }

ENABLE_OPERATORS_ON(Value)
ENABLE_OPERATORS_ON(PieceType)
ENABLE_OPERATORS_ON(Piece)
ENABLE_OPERATORS_ON(Color)
ENABLE_OPERATORS_ON(Depth)
ENABLE_OPERATORS_ON(Square)
ENABLE_OPERATORS_ON(File)
ENABLE_OPERATORS_ON(Rank)

/// Added operators for adding integers to a Value
inline Value operator+(Value v, int i) { return Value(int(v) + i); }
inline Value operator-(Value v, int i) { return Value(int(v) - i); }

ENABLE_SAFE_OPERATORS_ON(Score)

/// Only declared but not defined. We don't want to multiply two scores due to
/// a very high risk of overflow. So user should explicitly convert to integer.
inline Score operator*(Score s1, Score s2);

/// Division of a Score must be handled separately for each term
inline Score operator/(Score s, int i) {
  return make_score(mg_value(s) / i, eg_value(s) / i);
}

#undef ENABLE_OPERATORS_ON
#undef ENABLE_SAFE_OPERATORS_ON

const Value PawnValueMidgame   = Value(0x0C6);
const Value PawnValueEndgame   = Value(0x102);
const Value KnightValueMidgame = Value(0x331);
const Value KnightValueEndgame = Value(0x34E);
const Value BishopValueMidgame = Value(0x344);
const Value BishopValueEndgame = Value(0x359);
const Value RookValueMidgame   = Value(0x4F6);
const Value RookValueEndgame   = Value(0x4FE);
const Value QueenValueMidgame  = Value(0x9D9);
const Value QueenValueEndgame  = Value(0x9FE);

extern const Value PieceValueMidgame[17];
extern const Value PieceValueEndgame[17];
extern int SquareDistance[64][64];

inline Value mate_in(int ply) {
  return VALUE_MATE - ply;
}

inline Value mated_in(int ply) {
  return -VALUE_MATE + ply;
}

inline Piece make_piece(Color c, PieceType pt) {
  return Piece((c << 3) | pt);
}

inline PieceType type_of(Piece p)  {
  return PieceType(p & 7);
}

inline Color color_of(Piece p) {
  return Color(p >> 3);
}

inline Color flip(Color c) {
  return Color(c ^ 1);
}

inline Square make_square(File f, Rank r) {
  return Square((r << 3) | f);
}

inline bool square_is_ok(Square s) {
  return s >= SQ_A1 && s <= SQ_H8;
}

inline File file_of(Square s) {
  return File(s & 7);
}

inline Rank rank_of(Square s) {
  return Rank(s >> 3);
}

inline Square flip(Square s) {
  return Square(s ^ 56);
}

inline Square mirror(Square s) {
  return Square(s ^ 7);
}

inline Square relative_square(Color c, Square s) {
  return Square(s ^ (c * 56));
}

inline Rank relative_rank(Color c, Rank r) {
  return Rank(r ^ (c * 7));
}

inline Rank relative_rank(Color c, Square s) {
  return relative_rank(c, rank_of(s));
}

inline bool opposite_colors(Square s1, Square s2) {
  int s = s1 ^ s2;
  return ((s >> 3) ^ s) & 1;
}

inline int file_distance(Square s1, Square s2) {
  return abs(file_of(s1) - file_of(s2));
}

inline int rank_distance(Square s1, Square s2) {
  return abs(rank_of(s1) - rank_of(s2));
}

inline int square_distance(Square s1, Square s2) {
  return SquareDistance[s1][s2];
}

inline char piece_type_to_char(PieceType pt) {
  return " PNBRQK"[pt];
}

inline char file_to_char(File f) {
  return char(f - FILE_A + int('a'));
}

inline char rank_to_char(Rank r) {
  return char(r - RANK_1 + int('1'));
}

inline Square pawn_push(Color c) {
  return c == WHITE ? DELTA_N : DELTA_S;
}

inline Square from_sq(Move m) {
  return Square((m >> 6) & 0x3F);
}

inline Square to_sq(Move m) {
  return Square(m & 0x3F);
}

inline bool is_special(Move m) {
  return m & (3 << 14);
}

inline bool is_promotion(Move m) {
  return (m & (3 << 14)) == (1 << 14);
}

inline int is_enpassant(Move m) {
  return (m & (3 << 14)) == (2 << 14);
}

inline int is_castle(Move m) {
  return (m & (3 << 14)) == (3 << 14);
}

inline PieceType promotion_piece_type(Move m) {
  return PieceType(((m >> 12) & 3) + 2);
}

inline Move make_move(Square from, Square to) {
  return Move(to | (from << 6));
}

inline Move make_promotion(Square from, Square to, PieceType pt) {
  return Move(to | (from << 6) | (1 << 14) | ((pt - 2) << 12)) ;
}

inline Move make_enpassant(Square from, Square to) {
  return Move(to | (from << 6) | (2 << 14));
}

inline Move make_castle(Square from, Square to) {
  return Move(to | (from << 6) | (3 << 14));
}

inline bool is_ok(Move m) {
  return from_sq(m) != to_sq(m); // Catches also MOVE_NULL and MOVE_NONE
}

#include <string>

inline const std::string square_to_string(Square s) {
  char ch[] = { file_to_char(file_of(s)), rank_to_char(rank_of(s)), 0 };
  return ch;
}

/// Our insertion sort implementation, works with pointers and iterators and is
/// guaranteed to be stable, as is needed.
template<typename T, typename K>
void sort(K firstMove, K lastMove)
{
  T value;
  K cur, p, d;

  if (firstMove != lastMove)
      for (cur = firstMove + 1; cur != lastMove; cur++)
      {
          p = d = cur;
          value = *p--;
          if (*p < value)
          {
              do *d = *p;
              while (--d != firstMove && *--p < value);
              *d = value;
          }
      }
}

#endif // !defined(TYPES_H_INCLUDED)