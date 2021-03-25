package org.jlab.coda.eventViewer;


import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

/**
 * This is a simple GUI that displays an evio event in a tree. It allows the user to open event files and
 * dictionaries, and go event-by-event showing the event in a JTree. It can also view an evio event contained
 * in a cMsg message using any dictionary also contained in the message.
 * @author heddle
 */
@SuppressWarnings("serial")
public class EventTreeFrame extends JFrame  {

    /**
	 * Constructor for a simple tree viewer for evio files.
	 */
	public EventTreeFrame() {
		super("Jevio Event Tree");
		initializeLookAndFeel();

		// set the close to call System.exit
		WindowAdapter wa = new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				System.out.println("Exiting.");
				System.exit(0);
			}
		};
		addWindowListener(wa);

		// add all the components
		addComponents();

		// size to the screen
		sizeToScreen(this, 0.85);
	}

	/**
	 * Add the components to the frame.
	 */
	protected void addComponents() {
        setLayout(new BorderLayout());

        // Create JPanel which holds the tree display and related widgets
        EventTreePanelv6 eventTreePanel = new EventTreePanelv6();

         // Create JPanel for displaying event information
        EventInfoPanel eventInfoPanel = new EventInfoPanel();

        // Create object to make all menus (and 1 panel)
        EventTreeMenu eventTreeMenu = new EventTreeMenu(eventTreePanel, eventInfoPanel);

        // add Menus
		addMenus(eventTreeMenu);

        // Create JPanel to control events
        JPanel eventControlPanel = eventTreeMenu.addEventControlPanel();

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(eventControlPanel, BorderLayout.WEST);
        topPanel.add(eventInfoPanel, BorderLayout.CENTER);

        // Place tree panel in place
		add(eventTreePanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // Set a default dictionary from command line
        String tmp = System.getProperty("dictionary");
        if (tmp != null) {
            File dictFile = new File(tmp);
            if (dictFile.exists() && dictFile.isFile()) {
                eventTreeMenu.openDictionaryFile(dictFile);
            }
        }
    }

    /**
     * Add the menus to the frame.
     * @param eventTreeMenu object used to create all menus.
     */
	protected void addMenus(EventTreeMenu eventTreeMenu) {
		JMenuBar menuBar = new JMenuBar();
        menuBar.add(eventTreeMenu.createFileMenu());
        menuBar.add(eventTreeMenu.createViewMenu());
        menuBar.add(eventTreeMenu.createDictionaryMenu());
        menuBar.add(eventTreeMenu.createEventMenu());
        menuBar.add(eventTreeMenu.createFilterMenu());
		setJMenuBar(menuBar);
	}


	/**
	 * Size and center a JFrame relative to the screen.
	 *
	 * @param frame the frame to size.
	 * @param fractionalSize the fraction desired of the screen--e.g., 0.85 for 85%.
	 */
	private void sizeToScreen(JFrame frame, double fractionalSize) {
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		d.width = (int) (fractionalSize * .65 * d.width);
		d.height = (int) (fractionalSize * d.height);
		frame.setSize(d);
		centerComponent(frame);
	}

	/**
	 * Center a component.
	 *
	 * @param component the Component to center.
	 */
	private void centerComponent(Component component) {

		if (component == null)
			return;

		try {
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			Dimension componentSize = component.getSize();
			if (componentSize.height > screenSize.height) {
				componentSize.height = screenSize.height;
			}
			if (componentSize.width > screenSize.width) {
				componentSize.width = screenSize.width;
			}

			int x = ((screenSize.width - componentSize.width) / 2);
			int y = ((screenSize.height - componentSize.height) / 2);

			component.setLocation(x, y);

		}
		catch (Exception e) {
			component.setLocation(200, 200);
			e.printStackTrace();
		}
	}

	/**
	 * Initialize the look and feel.
	 */
	private void initializeLookAndFeel() {

		LookAndFeelInfo[] lnfinfo = UIManager.getInstalledLookAndFeels();

		if ((lnfinfo == null) || (lnfinfo.length < 1)) {
			return;
		}

		String desiredLookAndFeel = "Windows";

		for (int i = 0; i < lnfinfo.length; i++) {
			if (lnfinfo[i].getName().equals(desiredLookAndFeel)) {
				try {
					UIManager.setLookAndFeel(lnfinfo[i].getClassName());
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				return;
			}
		}
	}


    /**
     * Method to decode the command line used to start this application.
     * @param args command line arguments
     */
    private static void decodeCommandLine(String[] args) {
        // loop over all args
        for (String arg : args) {
            if (arg.equalsIgnoreCase("-h") ||
                    arg.equalsIgnoreCase("-help") ) {
                usage();
                System.exit(-1);
            }
            // ignore all other args (not -D which is special)
        }
    }


    /** Method to print out correct program command line usage. */
    private static void usage() {
        System.out.println("\nUsage:\n\n" +
                "   java org.jlab.coda.eventViewer.EventTreeFrame\n" +
                "        [-h]                   print this help\n" +
                "        [-help]                print this help\n" +
                "        [-DfilePath=xxx]       set default directory for data files\n" +
                "        [-DdictionaryPath=xxx] set default dictionary for dictionary files\n" +
                "        [-Ddictionary=xxx]     set name of default dictionary file\n");
    }


    /**
	 * Main program for launching the frame.
	 *
	 * @param args command line arguments--ignored.
	 */
	public static void main(String args[]) {

        decodeCommandLine(args);

        final EventTreeFrame frame = new EventTreeFrame();

		SwingUtilities.invokeLater(new Runnable() {
		    public void run() {
				frame.setVisible(true);
		    }
		});
	}

}