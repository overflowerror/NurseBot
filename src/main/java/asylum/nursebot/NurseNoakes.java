package asylum.nursebot;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import asylum.nursebot.commands.CommandHandler;
import asylum.nursebot.commands.CommandInterpreter;
import asylum.nursebot.loader.ModuleLoader;
import asylum.nursebot.objects.Locality;
import asylum.nursebot.objects.Module;
import asylum.nursebot.objects.Permission;
import asylum.nursebot.objects.Visibility;
import asylum.nursebot.persistence.Connector;
import asylum.nursebot.persistence.ModelManager;
import asylum.nursebot.persistence.modules.NurseModule;
import asylum.nursebot.semantics.SemanticsHandler;
import asylum.nursebot.utils.ConfigHolder;
import asylum.nursebot.utils.StringTools;
import asylum.nursebot.utils.log.Logger;
import asylum.nursebot.utils.log.LoggerImpl;
import org.javalite.activejdbc.InitException;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class NurseNoakes extends TelegramLongPollingBot {

	public static final String VERSION = "4.1";
	public static final List<String> BOT_ADMIN_USERNAMES = 
			Collections.unmodifiableList(Arrays.asList("overflowerror", "LinoBla"));
	private static final int EXIT_CODE_SHUTDOWN = 0;
	private static final int EXIT_CODE_RESTART = 1;

	private static final int EXIT_CODE_CRITICAL = 10;

	public static void main(String[] args) {
		if (Arrays.asList(args).contains("-v")) {
			System.out.println(VERSION);
			System.exit(EXIT_CODE_SHUTDOWN);
		}

		ApiContextInitializer.init();

		TelegramBotsApi botsApi = new TelegramBotsApi();

		try {
			botsApi.registerBot(new NurseNoakes());
		} catch (TelegramApiException e) {
			e.printStackTrace();
			System.exit(EXIT_CODE_CRITICAL);
		}

	}

	private CommandHandler commandHandler;
	private SemanticsHandler semanticsHandler;
	
	private Connector connector;
	
	private Set<Long> pausedChats = new HashSet<>();
	
	private Collection<Module> activeModules = new ConcurrentLinkedQueue<>();
	private Collection<Module> inactiveModules = new ConcurrentLinkedQueue<>();
	
	private long started;

	private Logger logger;
	
	public NurseNoakes() {
		started = new Date().getTime();

		LoggerImpl loggerSetup = Logger.getInstance();
		loggerSetup.setCrticalAction(() -> {
			System.err.println("Critical error occurred.");
			System.err.println("Shuting down.");
			System.exit(EXIT_CODE_CRITICAL);
		});

		logger = Logger.getModuleLogger("main");

		logger.info("Getting config file.");
		ConfigHolder holder = null;
		try {
			holder = ConfigHolder.getInstance();
		} catch (IOException e) {
			logger.critical("Error reading config file: " + e.getMessage());
		}

		logger.debug("Getting log verbosity.");
		if (holder.getLogVerbosity() != null) {
			switch (holder.getLogVerbosity().toLowerCase()) {
				case "debug":
					loggerSetup.setVerbosity(LoggerImpl.DEBUG);
					break;
				case "verbose":
					loggerSetup.setVerbosity(LoggerImpl.VERBOSE);
					break;
				case "info":
					loggerSetup.setVerbosity(LoggerImpl.INFO);
					break;
				case "warn":
					loggerSetup.setVerbosity(LoggerImpl.WARNING);
					break;
				case "error":
					loggerSetup.setVerbosity(LoggerImpl.ERROR);
					break;
				case "critical":
					loggerSetup.setVerbosity(LoggerImpl.CRITICAL);
					break;
				default:
					logger.warn("Invalid log verbosity in config file.");
					break;
			}
		}

		logger.debug("Getting logfile.");
		if (holder.getLogfile() != null) {
			try {
				loggerSetup.setLogfile(new File(holder.getLogfile()));
			} catch (IOException e) {
				logger.error("Error while opening logfile: " + e.getMessage());
			}
		}

		try {
			logger.info("Connecting to database.");
			connector = new Connector(holder.getDatabaseHost(), holder.getDatabaseSchema(), holder.getDatabaseUser(), holder.getDatabasePassword());
			logger.debug("Connecting setup thread to dataase.");
			connector.connectThread(); // setup thread

			logger.verbose("Building modules model.");
			ModelManager.build(NurseModule.class);
		} catch (InitException e) {
			logger.error("Error while connecting to database.");
			logger.exception(e);
			logger.error("Probably no instrumentation.");
			logger.critical("Unable to continue.");
		}

		logger.debug("Creating command handler.");
		commandHandler = new CommandHandler(this);
		logger.debug("Creating semantic handler.");
		semanticsHandler = new SemanticsHandler(this);

		logger.debug("Adding commands.");
		commandHandler.add(new CommandInterpreter(null)
				.setName("start")
				.setInfo("")
				.setVisibility(Visibility.PRIVATE)
				.setPermission(Permission.USER)
				.setLocality(Locality.USERS)
				.setAction(c -> {
					c.getSender().send(StringTools.makeBold("Hallo o/\nDieser Bot ist für Gruppen Chats gedacht, aber ein paar Funktionen sind auch hier nutzbar."), true);
				}));
		commandHandler.add(new CommandInterpreter(null)
				.setName("pause")
				.setInfo("")
				.setVisibility(Visibility.PRIVATE)
				.setPermission(Permission.ADMIN)
				.setLocality(Locality.GROUPS)
				.setPausable(false)
				.setAction(c -> {
					pausedChats.add(c.getMessage().getChatId());
					c.getSender().send("- paused -");
				}));
		commandHandler.add(new CommandInterpreter(null)
				.setName("resume")
				.setInfo("")
				.setVisibility(Visibility.PRIVATE)
				.setPermission(Permission.ADMIN)
				.setLocality(Locality.GROUPS)
				.setPausable(false)
				.setAction(c -> {
					pausedChats.remove(c.getMessage().getChatId());
					c.getSender().send("- resumed -");
				}));
		
		commandHandler.add(new CommandInterpreter(null)
				.setName("stop")
				.setInfo("")
				.setVisibility(Visibility.PRIVATE)
				.setPermission(Permission.ANY)
				.setLocality(Locality.EVERYWHERE)
				.setPausable(false)
				.setAction(c -> {
					if (BOT_ADMIN_USERNAMES.contains(c.getMessage().getFrom().getUserName())) {
						c.getSender().send("Okay, ich schalte mich jetzt ab.");
						logger.info("Got stop command.");
						stop();
					} else {
						c.getSender().reply("Du darfst das nicht.", c.getMessage());
					}
				}));
		
		commandHandler.add(new CommandInterpreter(null)
				.setName("restart")
				.setInfo("")
				.setVisibility(Visibility.PRIVATE)
				.setPermission(Permission.ANY)
				.setLocality(Locality.EVERYWHERE)
				.setPausable(false)
				.setAction(c -> {
					if (BOT_ADMIN_USERNAMES.contains(c.getMessage().getFrom().getUserName())) {
						c.getSender().send("Neustarten...");
						logger.info("Got restart command.");
						restart();
					} else {
						c.getSender().reply("Du darfst das nicht.", c.getMessage());
					}
				}));
		
		commandHandler.add(new CommandInterpreter(null)
				.setName("ping")
				.setInfo("ist der Bot noch aktiv")
				.setVisibility(Visibility.PRIVATE)
				.setPermission(Permission.ANY)
				.setLocality(Locality.EVERYWHERE)
				.setPausable(false)
				.setAction(c -> {
					c.getSender().reply(isChatPaused(c.getMessage().getChatId()) ? "Dieser Chat ist pausiert." : "pong", c.getMessage());
				}));
		
		commandHandler.add(new CommandInterpreter(null)
				.setName("version")
				.setInfo("zeigt die Version des Bots an")
				.setVisibility(Visibility.PRIVATE)
				.setPermission(Permission.ANY)
				.setLocality(Locality.EVERYWHERE)
				.setPausable(false)
				.setAction(c -> {
					c.getSender().reply("Version " + VERSION, c.getMessage());
				}));
		
		commandHandler.add(new CommandInterpreter(null)
				.setName("uptime")
				.setInfo("seit wann läuft der Bot")
				.setVisibility(Visibility.PRIVATE)
				.setPermission(Permission.ANY)
				.setLocality(Locality.EVERYWHERE)
				.setPausable(false)
				.setAction(c -> {
					long diff = new Date().getTime() - started;
					
					long s = diff / 1000;
					long m = s / 60;
					s %= 60;
					long h = m / 60;
					m %= 60;
					long d = h / 24;
					h %= 24;
					
					StringBuilder builder = new StringBuilder();
					builder.append("Dieser Bot läuft seit");
					boolean show = false;
					if (d > 0 || show) {
						builder.append(" ");
						show = true;
						builder.append(d).append(" ");
						builder.append("Tag");
						if (d != 1)
							builder.append("en");
					}
					if (h > 0 || show) {
						builder.append(" ");
						if (show && s == 0 && m == 0)
							builder.append("und ");
						show = true;
						builder.append(h).append(" ");
						builder.append("Stunde");
						if (h != 1)
							builder.append("n");
					}
					if (m > 0 || show) {
						builder.append(" ");
						if (show && s == 0)
							builder.append("und ");
						show = true;
						builder.append(m).append(" ");
						builder.append("Minute");
						if (m != 1)
							builder.append("n");
					}
					if (s > 0 || show) {
						builder.append(" ");
						if (show)
							builder.append("und ");
						show = true;
						builder.append(s).append(" ");
						builder.append("Sekunde");
						if (s != 1)
							builder.append("n");
					}
					builder.append(".");
					
					c.getSender().reply(builder.toString(), c.getMessage());
				}));
		
		commandHandler.add(new CommandInterpreter(null)
				.setName("info")
				.setInfo("zeigt Information zu diesem Bot an")
				.setVisibility(Visibility.PUBLIC)
				.setPermission(Permission.ANY)
				.setLocality(Locality.EVERYWHERE)
				.setPausable(false)
				.setAction(c -> {
					StringBuilder builder = new StringBuilder();
					
					builder.append(getBotUsername()).append(" ").append(VERSION).append("\n");
					
					builder.append("\n").append(StringTools.makeBold("Modules")).append("\n");
					for (Module module : activeModules) {
						builder.append("+ ").append(module.getName()).append(" (").append(module.getType().toString()).append(")\n");
					}
					for (Module module : inactiveModules) {
						builder.append("- ").append(module.getName()).append(" (").append(module.getType().toString()).append(")\n");
					}
					
					builder.append("\n").append(StringTools.makeBold("Commands")).append("\n");
					builder.append("There are currently ").append(commandHandler.getNumberOfEntities()).append(" commands installed.");
					builder.append("\n");
					
					builder.append("\n").append(StringTools.makeBold("Semantics")).append("\n");
					builder.append("There are currently ").append(semanticsHandler.getNumberOfEntities()).append(" semantic interpreters installed.");
					builder.append("\n");
					
					builder.append("\n").append(StringTools.makeBold("System")).append("\n");
					builder.append(StringTools.makeItalic("Operating System: ")).append(System.getProperty("os.name")).append("\n");
					builder.append(StringTools.makeItalic("Cores: ")).append(Runtime.getRuntime().availableProcessors()).append("\n");
					builder.append(StringTools.makeItalic("User: ")).append(System.getProperty("user.name")).append("\n");
					long maxMemory = Runtime.getRuntime().maxMemory();
					long freeMemory = Runtime.getRuntime().freeMemory();
					long usedMemory = maxMemory - freeMemory;
					builder.append(StringTools.makeItalic("Memory: ")).append(Math.round(((float) usedMemory) / 1024 / 1024 * 10)/10).append("/").append(Math.round(((float) maxMemory) / 1024 / 1024 * 10)/10).append(" MiB").append("\n");
					
					builder.append("\n");
					builder.append("Für einen Github Link klicke hier: /github");
					
					c.getSender().send(builder.toString(), true);
				}));

		commandHandler.add(new CommandInterpreter(null)
				.setName("github")
				.setInfo("Github-Link")
				.setVisibility(Visibility.PRIVATE)
				.setPermission(Permission.ANY)
				.setLocality(Locality.EVERYWHERE)
				.setAction(c -> {
					c.getSender().reply(StringTools.makeLink("Github Page", "https://github.com/overflowerror/NurseBot"), c.getMessage());
				}));

		commandHandler.add(new CommandInterpreter(null)
				.setName("modules")
				.setInfo("(de-)aktiviert Module")
				.setVisibility(Visibility.PUBLIC)
				.setPermission(Permission.ADMIN)
				.setLocality(Locality.GROUPS)
				.setPausable(false)
				.setAction(c -> {
					if (c.getParameter().length() < 2) {
						StringBuilder builder = new StringBuilder();
						
						builder.append("Synopsis: /modules [{+|-}MODUL]\n\n");
						
						builder.append("Aktuell sind folgende Module geladen:\n");
						
						for (Module module : activeModules) {
							builder.append("+ ").append(module.getName()).append(" (").append(module.getType().toString()).append(")\n");
						}
						for (Module module : inactiveModules) {
							builder.append("- ").append(module.getName()).append(" (").append(module.getType().toString()).append(")\n");
						}
						
						c.getSender().reply(builder.toString(), c.getMessage());
						return;
					}
					
					boolean activate = c.getParameter().substring(0, 1).equals("+");
					boolean deactivate = c.getParameter().substring(0, 1).equals("-");
					
					Module module = searchModule(c.getParameter().substring(1));
					
					if (module == null || activate == deactivate) {
						c.getSender().reply("Das schaut nicht richtig aus. Hast du dich vertippt?", c.getMessage());
						return;
					}
					
					try {
						if (activate) {
							activateModule(module);
							c.getSender().send("Das Modul " + module.getName() + " wurde aktiviert.");
						} else {
							deactivateModule(module);
							c.getSender().send("Das Modul " + module.getName() + " wurde deaktiviert.");
						}
					} catch (Exception e) {
						logger.warn("Module " + (activate ? "activiation" : "deactivation") + " failed.");
						logger.exception(e);
						c.getSender().reply("Der Vorgang ist fehlgeschlagen. Ist das Modul bereits " + (activate ? "aktiviert" : "deaktiviert") + "?", c.getMessage());
					}
					
				}));
		
		commandHandler.add(new CommandInterpreter(null)
				.setName("privacy")
				.setInfo("zeigt die Datenschutzerklärung an")
				.setVisibility(Visibility.PUBLIC)
				.setPermission(Permission.ANY)
				.setLocality(Locality.EVERYWHERE)
				.setPausable(false)
				.setAction(c -> {
					String text = "" +
						StringTools.makeItalic("Welche personenbezogenen Daten werden gespeichert?") + "\n" +
						"Benutzername und Benutzer-IDs werden zum Ablegen von Einstellungen in der Datenbank benutzt." + "\n\n" +
						StringTools.makeItalic("Wann werden personenbezogene Daten gespeichert?") + "\n" +
						"Personenbezogene Daten werden nur dann gespeichert, wenn sie benötigt werden." + "\n\n" +
						StringTools.makeItalic("Wofür werden personenbezogene Daten verwendet?") + "\n" +
						"Es werden nur Daten gespeichert, die für den Service direkt verwendet werden." + "\n\n" +
						StringTools.makeItalic("An wen werden personenbezogene Daten weitergegeben?") + "\n" +
						"Personenbezogene Daten werden nicht an Dritte weitergegeben." + "\n\n" +
						StringTools.makeItalic("Wann werden personenbezogene Daten wieder gelöscht?") + "\n" +
						"Daten werden gelöscht, sobald sie nicht mehr benötigt werden.";
					
						c.getSender().send(text, true);
				}));

		logger.debug("Creating module loader.");
		ModuleLoader loader = new ModuleLoader(this, commandHandler, semanticsHandler);

		logger.info("Loading dependencies.");
		loader.loadDependencies();
		logger.info("Loading regular modules.");
		loader.loadModules(this::loadModule);
			
			
		if (ModelManager.wasAnythingCreated()) {
			logger.warn("Changes to database were made.");
			restart();
		}

		logger.info("Activating modules.");
		List<NurseModule> registeredModules = NurseModule.findAll();
		for (NurseModule registeredModule : registeredModules) {
			Module module = searchModule(registeredModule.getName());
			if (module == null) {
				logger.warn("Module " + registeredModule.getName() + " does not exist in database. Deleting...");
				registeredModule.delete();
				continue;
			}
			if (registeredModule.isActive())
				activateModule(module);
		}

		logger.debug("End of setup. Disconnect from database.");
		connector.disconnectThread();
	}

	public void stop() {
		logger.info("Shuting down...");
		disableModules();

		logger.debug("Closing database connection.");
		connector.close();

		logger.info("Shutdown complete.");
		System.exit(EXIT_CODE_SHUTDOWN);
	}

	private void disableModules() {
		for(Module module : activeModules) {
			logger.verbose("Shutting down module " + module.getName() + "...");
			module.shutdown();
		}
		for(Module module : inactiveModules) {
			logger.verbose("Shutting down module " + module.getName() + "...");
			module.shutdown();
		}
	}

	public void restart() {
		logger.info("Restarting...");
		disableModules();

		connector.close();
		
		System.exit(EXIT_CODE_RESTART);
	}

	private Module searchModule(String name) {
		for (Module module : activeModules) {
			if (module.getName().equals(name)) {
				return module;
			}
		}
		for (Module module : inactiveModules) {
			if (module.getName().equals(name)) {
				return module;
			}
		}
		return null;
	}
	
	private void loadModule(Module module) {
		module.init();
		
		inactiveModules.add(module);
		
		logger.verbose("Module " + module.getName() + " loaded.");
	}
	
	private void activateModule(Module module) {
		if (!inactiveModules.remove(module))
			throw new IllegalArgumentException();
		
		activeModules.add(module);
		
		module.activate();
		
		NurseModule nm = NurseModule.byName(module.getName());
		if (nm == null) {
			nm = new NurseModule().setName(module.getName());
		}
		
		nm.setActive();
		
		nm.saveIt();
		
		logger.verbose("Module " + module.getName() + " activated.");
	}
	
	private void deactivateModule(Module module) {
		if (!activeModules.remove(module))
			throw new IllegalArgumentException();
		
		inactiveModules.add(module);
		
		module.deactivate();
		
		NurseModule nm = NurseModule.byName(module.getName());
		if (nm == null) {
			nm = new NurseModule().setName(module.getName());
		}
		
		nm.setInactive();
		
		nm.saveIt();
		
		logger.verbose("Module " + module.getName() + " deactivated.");
	}
	
	public boolean isActive(Module module) {
		return activeModules.contains(module);
	}
	
	@Override
	public String getBotUsername() {
		try {
			return ConfigHolder.getInstance().getTelegramUser();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void onUpdateReceived(Update update) {
		try {
			connector.connectThread();
		
			if (update.hasMessage()) {
				
				if (update.getMessage().isCommand()) {
					commandHandler.parse(update.getMessage());
				} else {
					semanticsHandler.parse(update.getMessage());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			connector.disconnectThread();
		}
	}

	@Override
	public String getBotToken() {
		try {
			return ConfigHolder.getInstance().getTelegramToken();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public boolean isChatPaused(Long chatid) {
		return pausedChats.contains(chatid);
	}

	public Connector getConnector() {
		return connector;
	}
}
