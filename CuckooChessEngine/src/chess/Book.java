/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package chess;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Implements an opening book.
 * @author petero
 */
public class Book {
    public static class BookEntry {
        Move move;
        int count;
        BookEntry(Move move) {
            this.move = move;
            count = 1;
        }
    }
    private static Map<Long, List<BookEntry>> bookMap;
    private static Random rndGen;
    private static int numBookMoves = -1;

    public Book(boolean verbose) {
        if (numBookMoves < 0) {
            initBook(verbose);
        }
    }

    private final void initBook(boolean verbose) {
        long t0 = System.currentTimeMillis();
        bookMap = new HashMap<Long, List<BookEntry>>();
        rndGen = new SecureRandom();
        rndGen.setSeed(System.currentTimeMillis());
        numBookMoves = 0;
    	try {
            InputStream inStream = getClass().getResourceAsStream("/book.bin");
            List<Byte> buf = new ArrayList<Byte>(8192);
            byte[] tmpBuf = new byte[1024];
            while (true) {
            	int len = inStream.read(tmpBuf);
            	if (len <= 0) break;
            	for (int i = 0; i < len; i++)
            		buf.add(tmpBuf[i]);
            }
            Position startPos = TextIO.readFEN(TextIO.startPosFEN);
	        Position pos = new Position(startPos);
	        UndoInfo ui = new UndoInfo();
	        int len = buf.size();
	        for (int i = 0; i < len; i += 2) {
	        	int b0 = buf.get(i); if (b0 < 0) b0 += 256;
	        	int b1 = buf.get(i+1); if (b1 < 0) b1 += 256;
	        	int move = (b0 << 8) + b1;
	        	if (move == 0) {
	        		pos = new Position(startPos);
	        	} else {
	        		Move m = new Move(move & 63, (move >> 6) & 63, (move >> 12) & 15);
	        		addToBook(pos, m);
	        		pos.makeMove(m, ui);
	        	}
	        }
        } catch (ChessParseError ex) {
            throw new RuntimeException();
        } catch (IOException ex) {
            System.out.println("Can't read opening book resource");
            throw new RuntimeException();
        }
        if (verbose) {
            long t1 = System.currentTimeMillis();
            System.out.printf("Book moves:%d (parse time:%.3f)%n", numBookMoves,
                    (t1 - t0) / 1000.0);
        }
    }

    /** Add a move to a position in the opening book. */
    private final void addToBook(Position pos, Move moveToAdd) {
        List<BookEntry> ent = bookMap.get(pos.zobristHash());
        if (ent == null) {
            ent = new ArrayList<BookEntry>();
            bookMap.put(pos.zobristHash(), ent);
        }
        for (int i = 0; i < ent.size(); i++) {
            BookEntry be = ent.get(i);
            if (be.move.equals(moveToAdd)) {
                be.count++;
                return;
            }
        }
        BookEntry be = new BookEntry(moveToAdd);
        ent.add(be);
        numBookMoves++;
    }

    /** Return a random book move for a position, or null if out of book. */
    public final Move getBookMove(Position pos) {
        List<BookEntry> bookMoves = bookMap.get(pos.zobristHash());
        if (bookMoves == null) {
            return null;
        }
        
        ArrayList<Move> legalMoves = new MoveGen().pseudoLegalMoves(pos);
        legalMoves = MoveGen.removeIllegal(pos, legalMoves);
        int sum = 0;
        for (int i = 0; i < bookMoves.size(); i++) {
            BookEntry be = bookMoves.get(i);
            if (!legalMoves.contains(be.move)) {
                // If an illegal move was found, it means there was a hash collision.
                return null;
            }
            sum += getWeight(bookMoves.get(i).count);
        }
        if (sum <= 0) {
            return null;
        }
        int rnd = rndGen.nextInt(sum);
        sum = 0;
        for (int i = 0; i < bookMoves.size(); i++) {
        	sum += getWeight(bookMoves.get(i).count);
            if (rnd < sum) {
                return bookMoves.get(i).move;
            }
        }
        // Should never get here
        throw new RuntimeException();
    }

    final private int getWeight(int count) {
        return (int)(Math.sqrt(count) * 100 + 1);
    }

    /** Return a string describing all book moves. */
    public final String getAllBookMoves(Position pos) {
        StringBuilder ret = new StringBuilder();
        List<BookEntry> bookMoves = bookMap.get(pos.zobristHash());
        if (bookMoves != null) {
            for (BookEntry be : bookMoves) {
                String moveStr = TextIO.moveToString(pos, be.move, false);
                ret.append(moveStr);
                ret.append("(");
                ret.append(be.count);
                ret.append(") ");
            }
        }
        return ret.toString();
    }

    /** Creates the book.bin file. */
    public static void main(String[] args) throws IOException {
    	List<Byte> binBook = createBinBook();
    	FileOutputStream out = new FileOutputStream("src/book.bin");
    	int bookLen = binBook.size();
    	byte[] binBookA = new byte[bookLen];
    	for (int i = 0; i < bookLen; i++)
    		binBookA[i] = binBook.get(i);
    	out.write(binBookA);
    	out.close();
    }
    
    public static List<Byte> createBinBook() {
    	List<Byte> binBook = new ArrayList<Byte>(0);
        try {
            InputStream inStream = new Object().getClass().getResourceAsStream("/book.txt");
            InputStreamReader inFile = new InputStreamReader(inStream);
            BufferedReader inBuf = new BufferedReader(inFile);
            LineNumberReader lnr = new LineNumberReader(inBuf);
            String line;
            while ((line = lnr.readLine()) != null) {
                if (line.startsWith("#") || (line.length() == 0)) {
                    continue;
                }
                if (!addBookLine(line, binBook)) {
                    System.out.printf("Book parse error, line:%d\n", lnr.getLineNumber());
                    throw new RuntimeException();
                }
//              System.out.printf("no:%d line:%s%n", lnr.getLineNumber(), line);
            }
        } catch (ChessParseError ex) {
            throw new RuntimeException();
        } catch (IOException ex) {
            System.out.println("Can't read opening book resource");
            throw new RuntimeException();
        }
    	return binBook;
    }

    /** Add a sequence of moves, starting from the initial position, to the binary opening book. */
    private static boolean addBookLine(String line, List<Byte> binBook) throws ChessParseError {
        Position pos = TextIO.readFEN(TextIO.startPosFEN);
        UndoInfo ui = new UndoInfo();
        String[] strMoves = line.split(" ");
        for (String strMove : strMoves) {
//            System.out.printf("Adding move:%s\n", strMove);
            Move m = TextIO.stringToMove(pos, strMove);
            if (m == null) {
                return false;
            }
            int val = m.from + (m.to << 6) + (m.promoteTo << 12);
            binBook.add((byte)(val >> 8));
            binBook.add((byte)(val & 255));
            pos.makeMove(m, ui);
        }
        binBook.add((byte)0);
        binBook.add((byte)0);
        return true;
    }
}
