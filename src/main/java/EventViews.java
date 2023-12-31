import java.util.Date;

import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;
import net.prominic.gja_v084.Event;
import net.prominic.gja_v084.GLogger;

public class EventViews extends Event {
	public Session session = null;
	public List<HashMap<String, Object>> events = null;

	public EventViews(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		for (int i = 0; i < events.size(); i++) {
			HashMap<String, Object> event = events.get(i);

			refreshView(event);	
		}
	}

	public void triggerHelperOnStart() {
		for (int i = 0; i < events.size(); i++) {
			HashMap<String, Object> event = events.get(i);
			boolean runOnStart = (Boolean) event.get("runOnStart");
			if (runOnStart) {
				refreshView(event);
			}
		}
	}

	public void triggerFireForce() {
		for (int i = 0; i < events.size(); i++) {
			HashMap<String, Object> event = events.get(i);
			refreshView(event);
		}
	}

	private void refreshView(HashMap<String, Object> event) {
		try {
			boolean runIfModified = (Boolean) event.get("runIfModified");
			Date lastRun = (Date) event.get("lastRun");
			Long interval = (Long) event.get("interval");

			String server = (String) event.get("server");
			String filePath = (String) event.get("filePath");
			Database database = session.getDatabase(server, filePath);

			if (database == null || !database.isOpen()) {
				String err = String.format("%s !! %s not found", server, filePath);
				this.getLogger().severe(err);
				System.err.print(err);
				return;
			}

			Vector<String> views = (Vector<String>) event.get("views");
			
			for (int i = 0; i<views.size(); i++) {
				String viewName = views.get(i);
				View view = database.getView(viewName);

				if (view == null) {
					database.recycle();
					String err = String.format("%s view not found in database %s", viewName, filePath);
					this.getLogger().severe(err);
					System.err.print(err);
					return;
				}

				boolean updated = false;
				if (runIfModified) {
					if (database.getLastModified().toJavaDate().compareTo(lastRun) > 0) {
						event.put("lastRun", new Date());
						updated = true;
						System.out.println(database.getTitle() + " - modified");
					}
				}
				
				if (!updated && interval > 0) {
					Date now = new Date();
					long seconds = (now.getTime()-lastRun.getTime())/1000;
					if (seconds >= interval) {
						updated = true;
						System.out.println(database.getTitle() + " - interval");
					};
				}

				if (updated) {
					System.out.println(view.getName() + ": refresh!");
					view.refresh();
					event.put("lastRun", new Date());
				}

				view.recycle();

			}

			database.recycle();
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}

}
