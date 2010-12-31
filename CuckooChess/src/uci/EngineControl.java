/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uci;

import chess.Book;
import chess.ComputerPlayer;
import chess.Move;
import chess.MoveGen;
import chess.Piece;
import chess.Position;
import chess.Search;
import chess.TextIO;
import chess.TranspositionTable;
import chess.TranspositionTable.TTEntry;
import chess.UndoInfo;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Control the search thread.
 * @author petero
 */
public class EngineControl {
    PrintStream os;

    Thread engineThread;
    private final Object threadMutex;
    Search sc;
    TranspositionTable tt;
    MoveGen moveGen;

    Position pos;
    long[] posHashList;
    int posHashListSize;
    boolean ponder;     // True if currently doing pondering
    boolean infinite;

    int minTimeLimit;
    int maxTimeLimit;
    int maxDepth;
    int maxNodes;
    List<Move> searchMoves;

    // Options
    int hashSizeMB = 16;
    boolean ownBook = false;
    boolean analyseMode = false;
    boolean ponderMode = true;

    /**
     * This class is responsible for sending "info" strings during search.
     */
    static class SearchListener implements Search.Listener {
        PrintStream os;
        
        SearchListener(PrintStream os) {
            this.os = os;
        }

        public void notifyDepth(int depth) {
            os.printf("info depth %d%n", depth);
        }

        public void notifyCurrMove(Move m, int moveNr) {
            os.printf("info currmove %s currmovenumber %d%n", moveToString(m), moveNr);
        }

        public void notifyPV(int depth, int score, int time, int nodes, int nps, boolean isMate,
                boolean upperBound, boolean lowerBound, ArrayList<Move> pv) {
            StringBuilder pvBuf = new StringBuilder();
            for (Move m : pv) {
                pvBuf.append(" ");
                pvBuf.append(moveToString(m));
            }
            String bound = "";
            if (upperBound) {
                bound = " upperbound";
            } else if (lowerBound) {
                bound = " lowerbound";
            }
            os.printf("info depth %d score %s %d%s time %d nodes %d nps %d pv%s%n",
                    depth, isMate ? "mate" : "cp", score, bound, time, nodes, nps, pvBuf.toString());
        }

        public void notifyStats(int nodes, int nps, int time) {
            os.printf("info nodes %d nps %d time %d%n", nodes, nps, time);
        }
    }

    public EngineControl(PrintStream os) {
        this.os = os;
        threadMutex = new Object();
        setupTT();
        moveGen = new MoveGen();
    }

    final public void startSearch(Position pos, ArrayList<Move> moves, SearchParams sPar) {
        setupPosition(new Position(pos), moves);
        computeTimeLimit(sPar);
        ponder = false;
        infinite = (maxTimeLimit < 0) && (maxDepth < 0) && (maxNodes < 0);
        startThread(minTimeLimit, maxTimeLimit, maxDepth, maxNodes);
        searchMoves = sPar.searchMoves;
    }

    final public void startPonder(Position pos, List<Move> moves, SearchParams sPar) {
        setupPosition(new Position(pos), moves);
        computeTimeLimit(sPar);
        ponder = true;
        infinite = false;
        startThread(-1, -1, -1, -1);
    }

    final public void ponderHit() {
        Search mySearch;
        synchronized (threadMutex) {
            mySearch = sc;
        }
        if (mySearch != null) {
            mySearch.timeLimit(minTimeLimit, maxTimeLimit);
        }
        infinite = (maxTimeLimit < 0) && (maxDepth < 0) && (maxNodes < 0);
        ponder = false;
    }

    final public void stopSearch() {
        stopThread();
    }

    final public void newGame() {
        tt.clear();
    }

    /**
     * Compute thinking time for current search.
     */
    final public void computeTimeLimit(SearchParams sPar) {
        minTimeLimit = -1;
        maxTimeLimit = -1;
        maxDepth = -1;
        maxNodes = -1;
        if (sPar.infinite) {
            minTimeLimit = -1;
            maxTimeLimit = -1;
            maxDepth = -1;
        } else if (sPar.depth > 0) {
            maxDepth = sPar.depth;
        } else if (sPar.mate > 0) {
            maxDepth = sPar.mate * 2 - 1;
        } else if (sPar.moveTime > 0) {
            minTimeLimit = maxTimeLimit = sPar.moveTime;
        } else if (sPar.nodes > 0) {
            maxNodes = sPar.nodes;
        } else {
            int moves = sPar.movesToGo;
            if (moves == 0) {
                moves = 999;
            }
            moves = Math.min(moves, 45); // Assume 45 more moves until end of game
            if (ponderMode) {
                final double ponderHitRate = 0.35;
                moves = (int)Math.ceil(moves * (1 - ponderHitRate));
            }
            boolean white = pos.whiteMove;
            int time = white ? sPar.wTime : sPar.bTime;
            int inc  = white ? sPar.wInc : sPar.bInc;
            final int margin = 1000;
            int timeLimit = (time + inc * (moves - 1) - margin) / moves;
            minTimeLimit = (int)(timeLimit * 0.85);
            maxTimeLimit = (int)(minTimeLimit * 2.5);

            // Leave at least 1s on the clock, but can't use negative time
            minTimeLimit = clamp(minTimeLimit, 1, time - margin);
            maxTimeLimit = clamp(maxTimeLimit, 1, time - margin);
        }
    }

    static final int clamp(int val, int min, int max) {
        if (val < min) {
            return min;
        } else if (val > max) {
            return max;
        } else {
            return val;
        }
    }

    final public void startThread(int minTimeLimit, int maxTimeLimit, final int maxDepth, final int maxNodes) {
        synchronized (threadMutex) {} // Must not start new search until old search is finished
        sc = new Search(pos, posHashList, posHashListSize, tt);
        sc.setListener(new SearchListener(os));
        ArrayList<Move> moves = moveGen.pseudoLegalMoves(pos);
        moves = MoveGen.removeIllegal(pos, moves);
        if ((searchMoves != null) && (searchMoves.size() > 0)) {
            moves.retainAll(searchMoves);
        }
        final ArrayList<Move> srchMoves = moves;
        if ((srchMoves.size() <= 1) && !infinite) {
            minTimeLimit = maxTimeLimit = 0;
        }
        final int srchMinTimeLimit = minTimeLimit;
        final int srchMaxTimeLimit = maxTimeLimit;
        engineThread = new Thread(new Runnable() {
            public void run() {
                Move m = null;
                if (ownBook && !analyseMode) {
                    Book book = new Book(false);
                    m = book.getBookMove(pos);
                }
                if (m == null) {
                    m = sc.iterativeDeepening(srchMoves, srchMinTimeLimit, srchMaxTimeLimit,
                            maxDepth, maxNodes, false);
                }
                while (ponder || infinite) {
                    // We should not respond until told to do so. Just wait until
                    // we are allowed to respond.
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
                Move ponderMove = getPonderMove(pos, m);
                synchronized (threadMutex) {
                    if (ponderMove != null) {
                        os.printf("bestmove %s ponder %s%n", moveToString(m), moveToString(ponderMove));
                    } else {
                        os.printf("bestmove %s%n", moveToString(m));
                    }
                    engineThread = null;
                    sc = null;
                }
            }
        });
        engineThread.start();
    }

    final public void stopThread() {
        Thread myThread;
        Search mySearch;
        synchronized (threadMutex) {
            myThread = engineThread;
            mySearch = sc;
        }
        if (myThread != null) {
            mySearch.timeLimit(0, 0);
            infinite = false;
            ponder = false;
            try {
                myThread.join();
            } catch (InterruptedException ex) {
                throw new RuntimeException();
            }
        }
    }


    private final void setupTT() {
        int nEntries = hashSizeMB > 0 ? hashSizeMB * (1 << 20) / 24 : 1024;
        int logSize = (int) Math.floor(Math.log(nEntries) / Math.log(2));
        tt = new TranspositionTable(logSize);
    }

    final void setupPosition(Position pos, List<Move> moves) {
        UndoInfo ui = new UndoInfo();
        posHashList = new long[200 + moves.size()];
        posHashListSize = 0;
        for (Move m : moves) {
            posHashList[posHashListSize++] = pos.zobristHash();
            pos.makeMove(m, ui);
        }
        this.pos = pos;
    }

    /**
     * Try to find a move to ponder from the transposition table.
     */
    final Move getPonderMove(Position pos, Move m) {
        Move ret = null;
        UndoInfo ui = new UndoInfo();
        pos.makeMove(m, ui);
        TTEntry ent = tt.probe(pos.historyHash());
        if (ent.type != TTEntry.T_EMPTY) {
            ret = new Move(0, 0, 0);
            ent.getMove(ret);
            ArrayList<Move> moves = moveGen.pseudoLegalMoves(pos);
            moves = MoveGen.removeIllegal(pos, moves);
            if (!moves.contains(ret)) {
                ret = null;
            }
        }
        pos.unMakeMove(m, ui);
        return ret;
    }

    static final String moveToString(Move m) {
        String ret = TextIO.squareToString(m.from);
        ret += TextIO.squareToString(m.to);
        switch (m.promoteTo) {
            case Piece.WQUEEN:
            case Piece.BQUEEN:
                ret += "q";
                break;
            case Piece.WROOK:
            case Piece.BROOK:
                ret += "r";
                break;
            case Piece.WBISHOP:
            case Piece.BBISHOP:
                ret += "b";
                break;
            case Piece.WKNIGHT:
            case Piece.BKNIGHT:
                ret += "n";
                break;
            default:
                break;
        }
        return ret;
    }

    static void printOptions(PrintStream os) {
        os.printf("option name Hash type spin default 16 min 1 max 2048%n");
        os.printf("option name OwnBook type check default false%n");
        os.printf("option name Ponder type check default true%n");
        os.printf("option name UCI_AnalyseMode type check default false%n");
        os.printf("option name UCI_EngineAbout type string default %s by Peter Osterlund, see http://web.comhem.se/petero2home/javachess/index.html%n",
                ComputerPlayer.engineName);
    }

    final void setOption(String optionName, String optionValue) {
        try {
            if (optionName.equals("hash")) {
                hashSizeMB = Integer.parseInt(optionValue);
                setupTT();
            } else if (optionName.equals("ownbook")) {
                ownBook = Boolean.parseBoolean(optionValue);
            } else if (optionName.equals("ponder")) {
                ponderMode = Boolean.parseBoolean(optionValue);
            } else if (optionName.equals("uci_analysemode")) {
                analyseMode = Boolean.parseBoolean(optionValue);
            }
        } catch (NumberFormatException nfe) {
        }
    }
}
