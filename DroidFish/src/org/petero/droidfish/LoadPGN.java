package org.petero.droidfish;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Vector;

import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

public class LoadPGN extends ListActivity {
	private static final class GameInfo {
		String event = "";
		String site = "";
		String date = "";
		String round = "";
		String white = "";
		String black = "";
		String result = "";
		long startPos;
		long endPos;

		public String toString() {
    		StringBuilder info = new StringBuilder(128);
    		info.append(white);
    		info.append(" - ");
    		info.append(black);
    		if (date.length() > 0) {
    			info.append(' ');
    			info.append(date);
    		}
    		if (round.length() > 0) {
    			info.append(' ');
	    		info.append(round);
    		}
    		if (event.length() > 0) {
    			info.append(' ');
    			info.append(event);
    		}
    		if (site.length() > 0) {
    			info.append(' ');
    			info.append(site);
    		}
    		info.append(' ');
    		info.append(result);
    		return info.toString();
		}
	}

	static Vector<GameInfo> gamesInFile = new Vector<GameInfo>();
	String fileName;
	ProgressDialog progress;
	static int defaultItem = 0;
	static String lastSearchString = "";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent i = getIntent();
		fileName = i.getAction();
		showDialog(PROGRESS_DIALOG);
		final LoadPGN lpgn = this;
		new Thread(new Runnable() {
			public void run() {
				readFile();
				runOnUiThread(new Runnable() {
					public void run() {
						lpgn.showList();
					}
				});
			}
		}).start();
	}

	private final void showList() {
		progress.dismiss();
		setContentView(R.layout.select_game);
		final ArrayAdapter<GameInfo> aa =
			new ArrayAdapter<GameInfo>(this, R.layout.select_game_list_item,
									   gamesInFile);
		setListAdapter(aa);
		ListView lv = getListView();
		lv.setSelectionFromTop(defaultItem, 0);
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
				defaultItem = pos;
				sendBackResult(aa.getItem(pos));
			}
		});

//		lv.setTextFilterEnabled(true);
		EditText filterText = (EditText)findViewById(R.id.select_game_filter);
		filterText.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable s) { }
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				aa.getFilter().filter(s);
				lastSearchString = s.toString();
			}
		});
		filterText.setText(lastSearchString);
		lv.requestFocus();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	final static int PROGRESS_DIALOG = 0;

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case PROGRESS_DIALOG:
			progress = new ProgressDialog(this);
			progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progress.setTitle(R.string.reading_pgn_file);
			progress.setMessage(getString(R.string.please_wait));
			progress.setCancelable(false);
			return progress;
		default:
			return null;
		}
	}

	static private final class BufferedRandomAccessFileReader {
		RandomAccessFile f;
		byte[] buffer = new byte[8192];
		long bufStartFilePos = 0;
		int bufLen = 0;
		int bufPos = 0;

		BufferedRandomAccessFileReader(String fileName) throws FileNotFoundException {
			f = new RandomAccessFile(fileName, "r");
		}
		final long length() throws IOException {
			return f.length();
		}
		final long getFilePointer() throws IOException {
			return bufStartFilePos + bufPos;
		}
		final void close() throws IOException {
			f.close();
		}

		private final int EOF = -1024;

		final String readLine() throws IOException {
			// First handle the common case where the next line is entirely 
			// contained in the buffer
			for (int i = bufPos; i < bufLen; i++) {
				byte b = buffer[i];
				if ((b == '\n') || (b == '\r')) {
					String line = new String(buffer, bufPos, i - bufPos);
					for ( ; i < bufLen; i++) {
						b = buffer[i];
						if ((b != '\n') && (b != '\r')) {
							bufPos = i;
							return line;
						}
					}
					break;
				}
			}

			// Generic case
			byte[] lineBuf = new byte[8192];
			int lineLen = 0;
			int b;
			while (true) {
				b = getByte();
				if (b == '\n' || b == '\r' || b == EOF)
					break;
				lineBuf[lineLen++] = (byte)b;
				if (lineLen >= lineBuf.length)
					break;
			}
			while (true) {
				b = getByte();
				if ((b != '\n') && (b != '\r')) {
					if (b != EOF)
						bufPos--;
					break;
				}
			}
			if ((b == EOF) && (lineLen == 0))
				return null;
			else
				return new String(lineBuf, 0, lineLen);
		}
		
		private final int getByte() throws IOException {
			if (bufPos >= bufLen) {
				bufStartFilePos = f.getFilePointer();
				bufLen = f.read(buffer);
				bufPos = 0;
				if (bufLen <= 0)
					return EOF;
			}
			return buffer[bufPos++];
		}
	}
	
	static long lastModTime = -1;
	static String lastFileName = "";
	
	private final void readFile() {
		if (!fileName.equals(lastFileName))
			defaultItem = 0;
		long modTime = new File(fileName).lastModified();
		if ((modTime == lastModTime) && fileName.equals(lastFileName))
			return;
		lastModTime = modTime;
		lastFileName = fileName;
		try {
			int percent = -1;
			gamesInFile.clear();
			BufferedRandomAccessFileReader f = new BufferedRandomAccessFileReader(fileName);
			long fileLen = f.length();
			GameInfo gi = null;
			boolean inHeader = false;
			long filePos = 0;
			while (true) {
				filePos = f.getFilePointer();
				String line = f.readLine();
				if (line == null)
					break; // EOF
				int len = line.length();
				if (len == 0)
					continue;
				boolean isHeader = line.charAt(0) == '[';
				if (isHeader) {
					if (!line.contains("\"")) // Try to avoid some false positives
						isHeader = false;
				}
				if (isHeader) {
					if (!inHeader) { // Start of game
						inHeader = true;
						if (gi != null) {
							gi.endPos = filePos;
							gamesInFile.add(gi);
							final int newPercent = (int)(filePos * 100 / fileLen);
							if (newPercent > percent) {
								percent =  newPercent;
								runOnUiThread(new Runnable() {
									public void run() {
										progress.setProgress(newPercent);
									}
								});
							}
						}
						gi = new GameInfo();
						gi.startPos = filePos;
						gi.endPos = -1;
					}
					if (line.startsWith("[Event ")) {
						gi.event = line.substring(8, len - 2);
						if (gi.event.equals("?")) gi.event = "";
					} else if (line.startsWith("[Site ")) {
						gi.site = line.substring(7, len - 2);
						if (gi.site.equals("?")) gi.site= "";
					} else if (line.startsWith("[Date ")) {
						gi.date = line.substring(7, len - 2);
						if (gi.date.equals("?")) gi.date= "";
					} else if (line.startsWith("[Round ")) {
						gi.round = line.substring(8, len - 2);
						if (gi.round.equals("?")) gi.round= "";
					} else if (line.startsWith("[White ")) {
						gi.white = line.substring(8, len - 2);
					} else if (line.startsWith("[Black ")) {
						gi.black = line.substring(8, len - 2);
					} else if (line.startsWith("[Result ")) {
						gi.result = line.substring(9, len - 2);
					}
				} else {
					inHeader = false;
				}
			}
			if (gi != null) {
				gi.endPos = filePos;
				gamesInFile.add(gi);
			}
			f.close();
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}
	}

	private final void sendBackResult(GameInfo gi) {
		try {
			RandomAccessFile f;
			f = new RandomAccessFile(fileName, "r");
			byte[] pgnData = new byte[(int) (gi.endPos - gi.startPos)];
			f.seek(gi.startPos);
			f.readFully(pgnData);
			f.close();
			String result = new String(pgnData);
			setResult(RESULT_OK, (new Intent()).setAction(result));
			finish();
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}
		setResult(RESULT_CANCELED);
		finish();
	}
}