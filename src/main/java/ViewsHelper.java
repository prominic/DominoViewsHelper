import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.Name;
import lotus.domino.View;
import lotus.domino.Document;
import net.prominic.gja_v085.JavaServerAddinGenesis;
import net.prominic.gja_v085.utils.DominoUtils;
import net.prominic.gja_v085.utils.StringUtils;

public class ViewsHelper extends JavaServerAddinGenesis {
	private String m_filePath = "viewshelper.nsf";
	// Scope of work: false (default) = only databases on this server (Server blank
	// or matching this server's name); true = every configured database regardless
	// of its Server field. Set once at load time via args[1] ("all").
	private boolean m_allServers = false;
	// Canonical name of the server this add-in runs on; resolved in
	// runNotesAfterInitialize and used to filter configs when m_allServers is false.
	private String m_serverName = "";
	EventViews m_event = null;

	public ViewsHelper(String[] args) {
		super(args);
		if (args != null && args.length > 0) {
			m_filePath = args[0];
		}
		if (args != null && args.length > 1) {
			m_allServers = "all".equalsIgnoreCase(args[1]);
		}
	}

	public ViewsHelper() {
		super();
	}

	@Override
	protected String getJavaAddinVersion() {
		return "1.0.5";
	}

	@Override
	protected String getJavaAddinDate() {
		return "2026-06-17 12:00";
	}

	protected boolean runNotesAfterInitialize() {
		try {
			m_serverName = canonical(m_session.getServerName());

			Database database = m_session.getDatabase(null, m_filePath);
			if (database == null || !database.isOpen()) {
				logSevere("(!) LOAD FAILED - database not found: " + m_filePath);
				return false;
			}

			m_event = new EventViews("Views", 1, true, this.m_logger);
			m_event.session = this.m_session;
			m_event.configs = getViews();
			eventsAdd(m_event);

			logMessage("scope: " + (m_allServers ? "all servers" : "current server (" + m_serverName + ")")
					+ " - " + m_event.configs.size() + " database(s)");
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
		} else if (cmd.startsWith("show")) {
			showStatus();
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

				// In current-server mode, skip databases that target another server.
				// A blank Server means "local" and always belongs to this server.
				if (!m_allServers) {
					String serverCanonical = canonical(server);
					if (!serverCanonical.isEmpty() && !serverCanonical.equalsIgnoreCase(m_serverName)) {
						DominoUtils.recycle(doc);
						doc = docNext;
						continue;
					}
				}

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
				event.put("lastRefresh", null);
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
		logMessage("   show             Show what each database refreshes, frequency, last/next run");
		logMessage("   trigger          Refresh all configured views now (from " + m_filePath + ")");
		logMessage("   update           Reload configuration from " + m_filePath);
	}

	/**
	 * Display run configuration
	 */
	protected void showInfoExt() {
		logMessage("config       " + m_filePath);
		logMessage("scope        " + (m_allServers ? "all servers" : "current server (" + m_serverName + ")"));
		logMessage("databases    " + m_event.configs.size());
	}

	/**
	 * Canonicalize a Notes name (e.g. "Server1/Org" -> "CN=Server1/O=Org") so that
	 * abbreviated and canonical Server values compare equal. Blank/null -> "".
	 */
	private String canonical(String name) {
		if (name == null || name.trim().isEmpty()) {
			return "";
		}
		try {
			Name n = m_session.createName(name);
			String result = n.getCanonical();
			DominoUtils.recycle(n);
			return result;
		} catch (Exception e) {
			logSevere(e);
			return name;
		}
	}

	/**
	 * Show, per configured database, what is refreshed, how often, and when it last/next runs.
	 */
	private void showStatus() {
		List<HashMap<String, Object>> configs = m_event.configs;
		if (configs == null || configs.isEmpty()) {
			logMessage("no databases configured in " + m_filePath);
			return;
		}

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		logMessage(configs.size() + " database(s) configured in " + m_filePath + ":");

		for (int i = 0; i < configs.size(); i++) {
			HashMap<String, Object> c = configs.get(i);

			String server = (String) c.get("server");
			String filePath = (String) c.get("filePath");
			String target = (server == null || server.isEmpty() ? "" : server + "!!") + filePath;

			String views;
			if (Boolean.TRUE.equals(c.get("allViews"))) {
				views = "ALL";
			} else {
				@SuppressWarnings("unchecked")
				Vector<String> v = (Vector<String>) c.get("views");
				views = (v == null || v.isEmpty()) ? "(none)" : v.size() + " (" + StringUtils.join(v, ", ") + ")";
			}

			long interval = (Long) c.get("interval");
			boolean runIfModified = (Boolean) c.get("runIfModified");
			Date lastRun = (Date) c.get("lastRun");
			Date lastRefresh = (Date) c.get("lastRefresh");

			StringBuilder schedule = new StringBuilder();
			if (runIfModified) schedule.append("on modify");
			if (interval > 0) {
				if (schedule.length() > 0) schedule.append(" + ");
				schedule.append("every ").append(interval).append("s (").append(humanInterval(interval)).append(")");
			}
			if (schedule.length() == 0) schedule.append("manual only (trigger)");

			logMessage("- " + target);
			logMessage("    views     : " + views);
			logMessage("    schedule  : " + schedule);
			logMessage("    last run  : " + (lastRefresh == null ? "not yet" : sdf.format(lastRefresh)));
			if (interval > 0 && lastRun != null) {
				logMessage("    next run  : ~" + sdf.format(new Date(lastRun.getTime() + interval * 1000L)));
			}
		}
	}

	/**
	 * Render a seconds interval in the largest whole unit (d/h/m/s) for readability.
	 */
	private static String humanInterval(long seconds) {
		if (seconds % 86400 == 0) return (seconds / 86400) + "d";
		if (seconds % 3600 == 0) return (seconds / 3600) + "h";
		if (seconds % 60 == 0) return (seconds / 60) + "m";
		return seconds + "s";
	}

}