import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.View;
import lotus.domino.Document;
import net.prominic.gja_v085.JavaServerAddinGenesis;
import net.prominic.gja_v085.utils.DominoUtils;

public class ViewsHelper extends JavaServerAddinGenesis {
	private String m_filePath = "viewshelper.nsf";
	EventViews m_event = null;

	public ViewsHelper(String[] args) {
		super(args);
		if (args != null && args.length > 0) {
			m_filePath = args[0];
		}
	}

	public ViewsHelper() {
		super();
	}

	@Override
	protected String getJavaAddinVersion() {
		return "1.0.4";
	}

	@Override
	protected String getJavaAddinDate() {
		return "2026-06-16 08:00";
	}
	
	protected boolean runNotesAfterInitialize() {
		try {
			Database database = m_session.getDatabase(null, m_filePath);
			if (database == null || !database.isOpen()) {
				logSevere("(!) LOAD FAILED - database not found: " + m_filePath);
				return false;
			}

			m_event = new EventViews("Views", 1, true, this.m_logger);
			m_event.session = this.m_session;
			m_event.configs = getViews();
			eventsAdd(m_event);
		} catch (Exception e) {
			logSevere(e);
			return false;
		}
		return true;
	}

	protected boolean resolveMessageQueueState(String cmd) {
		boolean flag = super.resolveMessageQueueState(cmd);
		if (flag)
			return true;

		if (cmd.startsWith("update")) {
			m_event.configs = getViews();
			logMessage("update - completed");
		} else if (cmd.startsWith("trigger")) {
			m_event.run();
			logMessage("trigger - completed");
		} else {
			logMessage("invalid command (use -h or help to get details)");
		}

		return true;
	}

	private List<HashMap<String, Object>> getViews() {
		List<HashMap<String, Object>> list = null;

		try {
			Database database = m_session.getDatabase(null, m_filePath);
			if (database == null || !database.isOpen()) {
				logMessage("(!) LOAD FAILED - database not found: " + m_filePath);
				return null;
			}

			list = new ArrayList<HashMap<String, Object>>();

			View view = database.getView("($views)");
			Document doc = view.getFirstDocument();
			while (doc != null) {
				Document docNext = view.getNextDocument(doc);

				// one config document = one target database
				String title = doc.getItemValueString("Title");

				String server = doc.getItemValueString("Server");
				String filePath = doc.getItemValueString("Database");
				@SuppressWarnings("unchecked")
				Vector<String> views = doc.getItemValue("Views");
				long interval = doc.getItemValueInteger("interval");
				boolean runIfModified = doc.getItemValueString("runIfModified").equals("1");
				boolean allViews = doc.getItemValueString("AllViews").equals("1");
				String log = doc.getItemValueString("Log");

				HashMap<String, Object> event = new HashMap<String, Object>();
				event.put("title", title);
				event.put("server", server);
				event.put("filePath", filePath);
				event.put("views", views);
				event.put("allViews", allViews);
				event.put("interval", interval);
				event.put("runIfModified", runIfModified);
				event.put("lastRun", new Date());
				event.put("log", log);
				
				list.add(event);

				DominoUtils.recycle(doc);
				doc = docNext;
			}

			DominoUtils.recycle(view, database);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return list;
	}

	protected void showHelpExt() {
		logMessage("   trigger          Refresh all configured views now (from " + m_filePath + ")");
		logMessage("   update           Reload configuration from " + m_filePath);
	}

	/**
	 * Display run configuration
	 */
	protected void showInfoExt() {
		logMessage("config       " + m_filePath);
		logMessage("databases    " + m_event.configs.size());
	}

}