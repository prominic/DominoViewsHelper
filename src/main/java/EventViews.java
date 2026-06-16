import java.util.Date;

import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.DateTime;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;
import net.prominic.gja_v085.Event;
import net.prominic.gja_v085.GLogger;
import net.prominic.gja_v085.utils.DominoUtils;

public class EventViews extends Event {
	public Session session = null;
	public List<HashMap<String, Object>> configs = null;

	public EventViews(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		for (int i = 0; i < configs.size(); i++) {
			HashMap<String, Object> config = configs.get(i);

			refreshView(config);
		}
	}

	private void refreshView(HashMap<String, Object> config) {
		try {
			boolean runIfModified = (Boolean) config.get("runIfModified");
			Date lastRun = (Date) config.get("lastRun");
			Long interval = (Long) config.get("interval");
			String server = (String) config.get("server");
			String filePath = (String) config.get("filePath");
			Database database = session.getDatabase(server, filePath);
			String log = (String) config.get("log");

			if (database == null || !database.isOpen()) {
				String err = String.format("%s !! %s not found", server, filePath);
				logMessage(log, err, true);
				return;
			}

			// Decide once per database whether a refresh is due, so that every
			// configured view is refreshed on the same pass (not just the first one).
			boolean updated = false;
			if (runIfModified) {
				DateTime lastModified = database.getLastModified();
				if (lastModified != null) {
					boolean modified = lastModified.toJavaDate().compareTo(lastRun) > 0;
					DominoUtils.recycle(lastModified);
					if (modified) {
						updated = true;
						logMessage(log, database.getTitle() + " - modified", false);
					}
				}
			}
			if (!updated && interval > 0) {
				long seconds = (new Date().getTime() - lastRun.getTime()) / 1000;
				if (seconds >= interval) {
					updated = true;
					logMessage(log, database.getTitle() + " - interval", false);
				}
			}

			if (!updated) {
				DominoUtils.recycle(database);
				return;
			}

			// Resolve the views to refresh. "All views" is evaluated live on every
			// pass, so views added to / removed from the database are picked up
			// automatically without re-running the config.
			boolean allViews = Boolean.TRUE.equals(config.get("allViews"));

			Vector<View> views = new Vector<View>();
			if (allViews) {
				@SuppressWarnings("unchecked")
				Vector<View> dbViews = database.getViews();
				views.addAll(dbViews);
			} else {
				@SuppressWarnings("unchecked")
				Vector<String> viewNames = (Vector<String>) config.get("views");
				for (int i = 0; i < viewNames.size(); i++) {
					String viewName = viewNames.get(i);
					View view = database.getView(viewName);

					if (view == null) {
						String err = String.format("%s view not found in database %s", viewName, filePath);
						logMessage(log, err, true);
						continue;
					}
					views.add(view);
				}
			}

			for (int i = 0; i<views.size(); i++) {
				View view = views.get(i);
				logMessage(log, view.getName() + ": refresh!", false);
				view.refresh();
				DominoUtils.recycle(view);
			}

			config.put("lastRun", new Date());
			DominoUtils.recycle(database);
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}

	private void logMessage(String logOpt, String message, boolean severe) {
		if (logOpt.equals("1") || logOpt.equals("2")) {
			if (severe) {
				getLogger().severe(message);				
			}
			else {
				getLogger().info(message);				
			}
		}
		if (logOpt.equals("2")) {
			if (severe) {
				System.err.print(message);
			}
			else {
				System.out.print(message);
			}
		}
	}
}
