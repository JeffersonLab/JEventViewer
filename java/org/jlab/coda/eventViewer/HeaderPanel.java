package org.jlab.coda.eventViewer;

import org.jlab.coda.jevio.BaseStructure;
import org.jlab.coda.jevio.BaseStructureHeader;
import org.jlab.coda.hipo.CompressionType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * This is a panel that displays the information from a BaseStructureHeader.
 * @author heddle
 */
@SuppressWarnings("serial")
public class HeaderPanel extends JPanel {

	/** Number of bytes in selected structure. */
	private NamedLabel evioVersion;

	/** Number of bytes in selected structure. */
	private NamedLabel compressed;

	/** Number of bytes in selected structure. */
	private NamedLabel lengthLabel;

    /** Type of selected structure: BANK, SEGMENT, or TAGSEGMENT. */
	private NamedLabel structureLabel;

    /** Type of data in selected structure. */
	private NamedLabel dataTypeLabel;

    /** Tag of selected structure. */
	private NamedLabel tagLabel;

    /** Number (num) of selected structure. */
	private NamedLabel numberLabel;

    /** Description of selected structure from dictionary. */
	private NamedLabel descriptionLabel;


    /**
     * Constructor.
     */
	public HeaderPanel() {
        setLayout(new GridLayout(2, 1, 0, 3)); // rows, cols, hgap, vgap
        setBorder(new EmptyBorder(5, 5, 2, 0));   // top, left, bot, right

		evioVersion = new NamedLabel("version",   "compression", 100);
		compressed  = new NamedLabel("compression",   "compression", 100);

		structureLabel = new NamedLabel("structure",   "compression", 150);
		dataTypeLabel  = new NamedLabel("data type",   "compression", 150);

		tagLabel    = new NamedLabel(   "tag", "number", 100);
		numberLabel = new NamedLabel("number", "number", 100);

        lengthLabel      = new NamedLabel(     "length", "compression", 300);
        descriptionLabel = new NamedLabel("description", "compression", 300);

        // limit size of labels
        Dimension d1 = structureLabel.getPreferredSize();
        Dimension d2 = descriptionLabel.getPreferredSize();

		evioVersion.setMaximumSize(d1);
		compressed.setMaximumSize(d1);
		structureLabel.setMaximumSize(d1);
		dataTypeLabel.setMaximumSize(d1);
        tagLabel.setMaximumSize(d1);
        numberLabel.setMaximumSize(d1);
        lengthLabel.setMaximumSize(d2);
        descriptionLabel.setMaximumSize(d2);

		JPanel p0 = createLayoutPanel();
		JPanel p1 = createLayoutPanel();

		p0.add(evioVersion);
		p0.add(Box.createRigidArea(new Dimension(5,0)));
		p0.add(structureLabel);
		p0.add(Box.createRigidArea(new Dimension(5,0)));
		p0.add(tagLabel);
        p0.add(Box.createRigidArea(new Dimension(5,0)));
		p0.add(lengthLabel);

		p1.add(compressed);
		p1.add(Box.createRigidArea(new Dimension(5,0)));
		p1.add(dataTypeLabel);
		p1.add(Box.createRigidArea(new Dimension(5,0)));
		p1.add(numberLabel);
        p1.add(Box.createRigidArea(new Dimension(5,0)));
		p1.add(descriptionLabel);

		add(p0);
		add(p1);
	}

	private JPanel createLayoutPanel() {
		JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		return p;
	}

	/**
	 * Set the fields in the panel based on the data in the header.
	 * @param structure the structure to use to set the fields, mostly from its header.
	 * @param version evio version of data.
	 * @param cType compression type of data.
	 */
	public void setHeader(BaseStructure structure, int version, CompressionType cType) {

		if ((structure == null) || (structure.getHeader() == null)) {
			evioVersion.setText("   ");
			compressed.setText("   ");
			structureLabel.setText("   ");
			lengthLabel.setText("   ");
			tagLabel.setText("   ");
			dataTypeLabel.setText("   ");
			numberLabel.setText("   ");
			descriptionLabel.setText("   ");
		}
		else {
			BaseStructureHeader header = structure.getHeader();
			if (version < 2) {
				evioVersion.setText("   ");
			}
			else {
				evioVersion.setText("" + version);
			}

			if (cType != null) {
				compressed.setText(cType.getDescription());
			}
			else {
				compressed.setText("   ");
			}

			structureLabel.setText("" + structure.getStructureType());
			lengthLabel.setText(4*header.getLength() + " bytes");
			tagLabel.setText("" + header.getTag());
			dataTypeLabel.setText("" + header.getDataType());
			numberLabel.setText("" + header.getNumber());
			descriptionLabel.setText(structure.getDescription());
		}
	}

    /**
     * Set the dictionary description in header panel.
     * @param structure event being described
     */
    public void setDescription(BaseStructure structure) {
        if (structure == null) {
            descriptionLabel.setText("   ");
            return;
        }
        descriptionLabel.setText(structure.getDescription());
    }

}