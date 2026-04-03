import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.awt.image.BufferedImage;

public class IntraGo extends JFrame {
    private Image mapImg;
    private int imgW, imgH;

    // map state
    private int clickX = -1, clickY = -1;                 // current location in original-map coords
    private boolean selectingCurrentLocation = false;

    // places and UI state
    private java.util.List<Place> places = new ArrayList<>();
    private String selectedCategory = "ALL";
    private Place nearestPlace = null;
    private Place selectedSidebarPlace = null;

    // route (in screen coordinates) between current and nearest
    private java.util.List<Point> currentRoute = new ArrayList<>();

    // UI components
    private DefaultListModel<String> sidebarModel = new DefaultListModel<>();
    private JList<String> sidebarList = new JList<>(sidebarModel);
    private JLabel nearestLabel = new JLabel("Nearest: None");
    private JTextArea descriptionArea = new JTextArea(6, 20);

    public IntraGo() {
        // try loading image
        try {
            mapImg = new ImageIcon("intragomap.jpg").getImage();
            imgW = mapImg.getWidth(null);
            imgH = mapImg.getHeight(null);
            if (imgW <= 0 || imgH <= 0) throw new RuntimeException("Image not loaded properly");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Could not load intragomap.jpg. Put the image in the program folder.");
            // create a placeholder blank image so program can still run
            imgW = 1000; imgH = 1500;
            mapImg = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
            Graphics g = mapImg.getGraphics();
            g.setColor(Color.WHITE); g.fillRect(0,0,imgW,imgH);
            g.setColor(Color.BLACK); g.drawString("Missing intragomap.jpg", 20, 20);
            g.dispose();
        }

        setTitle("IntraGo");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1500, 760);
        setLayout(new BorderLayout());

        // LEFT SIDEBAR
        JPanel left = new JPanel(new BorderLayout(6,6));
        left.setPreferredSize(new Dimension(400, 600));

        // category + set-location button
        String[] categories = {"ALL", "SCHOOL", "CHURCH", "RESTAURANT", "MUSEUM"};
        JComboBox<String> categoryBox = new JComboBox<>(categories);
        JButton setLocationBtn = new JButton("Set Current Location");

        JPanel topLeft = new JPanel(new BorderLayout(4,4));
        topLeft.add(categoryBox, BorderLayout.CENTER);
        topLeft.add(setLocationBtn, BorderLayout.EAST);

        left.add(topLeft, BorderLayout.NORTH);

        // sidebar list in center
        sidebarList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScroll = new JScrollPane(sidebarList);
        left.add(listScroll, BorderLayout.CENTER);

        // nearest + description at bottom
        JPanel bottomLeft = new JPanel(new BorderLayout(4,4));
        nearestLabel.setPreferredSize(new Dimension(220, 40));
        bottomLeft.add(nearestLabel, BorderLayout.NORTH);

        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(descriptionArea);
        bottomLeft.add(descScroll, BorderLayout.CENTER);

        left.add(bottomLeft, BorderLayout.SOUTH);

        add(left, BorderLayout.WEST);

        // CENTER MAP PANEL
        JPanel mapPanel = new JPanel() {
            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = (Graphics2D) g0.create();

                int pw = getWidth();
                int ph = getHeight();
                // draw scaled map
                g.drawImage(mapImg, 0, 0, pw, ph, null);

                // draw route first if exists
                if (currentRoute.size() >= 2) {
                    g.setStroke(new BasicStroke(4f));
                    g.setColor(Color.YELLOW);
                    for (int i = 0; i < currentRoute.size()-1; i++) {
                        Point a = currentRoute.get(i);
                        Point b = currentRoute.get(i+1);
                        g.drawLine(a.x, a.y, b.x, b.y);
                    }
                }

                // draw places (only those matching filter)
                for (Place p : places) {
                    if (!selectedCategory.equals("ALL") && !p.category.equals(selectedCategory)) continue;
                    int px = (int)((p.x / (double)imgW) * pw);
                    int py = (int)((p.y / (double)imgH) * ph);

                    // highlight selected sidebar place
                    if (p == selectedSidebarPlace) {
                        g.setColor(Color.MAGENTA);
                        g.fillOval(px-9, py-9, 18, 18);
                    } else {
                        g.setColor(Color.RED);
                        g.fillOval(px-6, py-6, 12, 12);
                    }

                    g.setColor(Color.BLACK);
                    g.setFont(g.getFont().deriveFont(12f));
                    g.drawString(p.name, px + 10, py - 6);
                }

                // draw current location (blue dot)
                if (clickX >= 0 && clickY >= 0) {
                    int cx = (int)((clickX / (double)imgW) * pw);
                    int cy = (int)((clickY / (double)imgH) * ph);
                    g.setColor(Color.BLUE);
                    g.fillOval(cx-6, cy-6, 12, 12);
                }

                // draw nearest place highlight (green)
                if (nearestPlace != null) {
                    int nx = (int)((nearestPlace.x / (double)imgW) * pw);
                    int ny = (int)((nearestPlace.y / (double)imgH) * ph);
                    g.setColor(Color.GREEN);
                    g.fillOval(nx-8, ny-8, 16, 16);
                }

                g.dispose();
            }
        };

        // mouse listener for map clicks
        mapPanel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (!selectingCurrentLocation) return; // not in selection mode
                if (selectedCategory.equals("ALL")) {
                    JOptionPane.showMessageDialog(null, "Please select a category (not ALL) before setting current location.");
                    selectingCurrentLocation = false;
                    return;
                }

                int pw = mapPanel.getWidth();
                int ph = mapPanel.getHeight();

                clickX = (int)((e.getX() / (double)pw) * imgW);
                clickY = (int)((e.getY() / (double)ph) * imgH);

                // find nearest place in the selected category
                nearestPlace = findNearestPlace();

                // build route (straight line) in screen coords
                buildRoute(mapPanel.getWidth(), mapPanel.getHeight());

                // show nearest info
                if (nearestPlace != null) {
                    nearestLabel.setText("Nearest: " + nearestPlace.name);
                    descriptionArea.setText(nearestPlace.description);
                } else {
                    nearestLabel.setText("Nearest: None");
                    descriptionArea.setText("");
                }

                selectingCurrentLocation = false; // turn off selection
                mapPanel.repaint();
            }
        });

        add(mapPanel, BorderLayout.CENTER);

        // ---- listeners for sidebar controls ----
        categoryBox.addActionListener(e -> {
            selectedCategory = (String) categoryBox.getSelectedItem();
            refreshSidebar(selectedCategory);
            nearestPlace = null;
            selectedSidebarPlace = null;
            descriptionArea.setText("");
            currentRoute.clear();
            repaint();
        });

        setLocationBtn.addActionListener(e -> {
            if (selectedCategory.equals("ALL")) {
                JOptionPane.showMessageDialog(null, "Select a specific category first (not ALL).");
                return;
            }
            selectingCurrentLocation = true;
            nearestLabel.setText("Click on the map to set your location...");
        });

        sidebarList.addListSelectionListener(e -> {
            int idx = sidebarList.getSelectedIndex();
            if (idx >= 0) {
                String name = sidebarModel.getElementAt(idx);
                // find place by name
                for (Place p : places) if (p.name.equals(name)) { selectedSidebarPlace = p; break; }
                if (selectedSidebarPlace != null) {
                    descriptionArea.setText(selectedSidebarPlace.description);
                    // center map or highlight: simply repaint
                    repaint();
                }
            }
        });

		//restaurants
		addPlace("Barbara's Heritage Restaurant", 332, 373, "RESTAURANT",
			"Barbara's Heritage Restaurant is known for its classic Filipino-Spanish dishes served in a heritage-inspired setting. The restaurant is located in the heart of Intramuros, making it popular among tourists. It offers cultural dinner shows and a nostalgic ambiance that complements its traditional menu.");
		addPlace("ALT Cafe and Restaurant", 183, 223, "RESTAURANT",
			"ALT Cafe and Restaurant is a cozy spot that offers a mix of modern comfort food and refreshing beverages. Its casual ambiance makes it a popular stop for students and visitors. The menu features hearty meals perfect for quick dining or long study sessions.");
		addPlace("Pares Kimchi", 220, 259, "RESTAURANT",
			"Pares Kimchi blends Filipino pares favorites with a Korean twist. It is known for affordable meals that appeal to both locals and students in the area. Visitors enjoy the flavorful beef pares paired with kimchi for a unique taste experience.");
		addPlace("J's Cuisine", 368, 131, "RESTAURANT",
			"J's Cuisine is a simple dining spot offering a selection of Filipino comfort dishes. Many customers appreciate the quick service and generous portions. It's a convenient choice for both tourists and workers around Intramuros.");
		addPlace("Black Scoop", 437, 243, "RESTAURANT",
			"Black Scoop Café is well-known for its milk teas, coffee selections, and dessert offerings. The interior provides a relaxed environment suitable for studying or casual meet-ups. Many customers enjoy their signature drinks and soft-serve treats.");
		addPlace("Casa Nueva Cafe Restaurant", 177, 337, "RESTAURANT",
			"Casa Nueva Bistro is a charming place that serves a mix of Filipino classics and modern café dishes. Its rustic decor and quiet ambience attract both tourists and locals. The restaurant is also known for its well-plated meals and refreshing beverages.");
		addPlace("Plaza San Luis Complex", 291, 336, "RESTAURANT",
			"Plaza San Luis Complex features several shops and restaurants offering traditional Filipino cuisine. It is part of a restored heritage block that highlights Spanish-era architecture. The area is ideal for visitors looking to dine while immersing themselves in history.");
		addPlace("Vitan's Eatery", 429, 447, "RESTAURANT",
			"Vitan's Eatery offers affordable Filipino home-style meals popular among students and workers. Its menu features classic ulam dishes served in a carinderia-style setup. Many patrons appreciate the quick service and budget-friendly prices.");
		addPlace("Cafe Sofia by Patio Victoria", 340, 515, "RESTAURANT",
			"Cafe Sofia by Patio Victoria serves a range of café dishes, pastries, and beverages in a relaxing environment. Its elegant interior makes it appealing for both casual and romantic dining. Guests often enjoy the scenic surroundings and peaceful ambience.");
		addPlace("Ilustrado Restaurant", 392, 527, "RESTAURANT",
			"Ilustrado Restaurant is a historic fine-dining establishment known for its Spanish-Filipino cuisine. It is highly regarded for signature dishes such as paella and callos. The heritage setting adds to its charm, making it a venue for special occasions.");

				//schools
		addPlace("Lyceum of the PH", 383, 121, "SCHOOL",
			"Lyceum of the Philippines University is a well-known institution offering a variety of academic programs. It is especially recognized for its hospitality, tourism, and international relations courses. The campus is vibrant and frequented by students from all throughout Metro Manila.");
		addPlace("Colegio de San Juan de Letran", 323, 101, "SCHOOL",
			"Colegio de San Juan de Letran is one of the oldest educational institutions in the Philippines. It is famous for its rich history and strong academic programs in business and liberal arts. The school maintains traditions that reflect its centuries-old heritage.");
		addPlace("Instituto Cervantes - Manila", 235, 372, "SCHOOL",
			"Instituto Cervantes serves as Spain’s official cultural center in Manila. It offers Spanish language courses and cultural events that promote Hispanic arts and literature. The facility is popular among language learners and international culture enthusiasts.");
		addPlace("Mapúa University", 418, 302, "SCHOOL",
			"Mapúa University is a leading institution in engineering, architecture, and technological studies. It is known for its innovative programs and strong focus on research. Students appreciate its modern facilities and industry-aligned curriculum.");
		addPlace("Manila HS", 432, 426, "SCHOOL",
			"Manila High School is one of the oldest public educational institutions in the city. It provides quality secondary education to residents of Manila. The school is also known for various programs that support student development.");
		addPlace("Industrial Advancement Academy of the Philippines", 399, 451, "SCHOOL",
			"Industrial Advancement Academy of the Philippines focuses on practical training in technical and industrial skills. Its programs prepare students for real-world careers in various industries. The institution emphasizes hands-on learning and workforce readiness.");
		addPlace("Pamantasan ng Lungsod ng Maynila", 232, 485, "SCHOOL",
			"Pamantasan ng Lungsod ng Maynila is a premier public university offering a wide range of academic programs. It is recognized for excellence in fields such as health sciences, law, and education. The campus fosters a strong academic community committed to public service.");

				//churches
		addPlace("San Agustin Church", 277, 399, "CHURCH",
			"San Agustin Church is the oldest stone church in the Philippines and a UNESCO World Heritage Site. It is admired for its baroque architecture and historical significance. Many visitors come to appreciate its ornate interior and centuries-old artifacts.");
		addPlace("The Manila Cathedral", 235, 269, "CHURCH",
			"The Manila Cathedral is one of the most iconic religious structures in the country. It features beautiful stained-glass windows and grand architectural design. Visitors often admire its impressive façade and peaceful atmosphere.");
		addPlace("Tempoy Church", 420, 314, "CHURCH",
			"Tempoy Church is a modest worship place attended by nearby residents. Despite its simplicity, it serves as a spiritual refuge for the community. Regular gatherings and small religious events take place here.");
		addPlace("Mt. Peace", 100, 395, "CHURCH",
			"Mt. Peace is a small church known for its serene surroundings. The place is frequented by individuals seeking quiet prayer time. Its minimalist structure adds to the peaceful ambience of the area.");
		addPlace("Archdiocese of Manila", 89, 366, "CHURCH",
			"The Archdiocese of Manila office oversees numerous parishes and religious institutions in the region. It plays a significant role in the administration of Catholic activities. Many visitors come here for official church documents and consultations.");
		addPlace("Joy Christian Fellowship", 299, 339, "CHURCH",
			"Joy Christian Fellowship is a modern worship center known for its welcoming community. The church holds regular services, youth gatherings, and Bible studies. Its friendly environment attracts both families and students.");
		addPlace("Catholic Bishops' Conference of the PH", 206, 363, "CHURCH",
			"The Catholic Bishops' Conference of the Philippines is the administrative body for Catholic bishops nationwide. It hosts meetings, publications, and religious programs. The location is often visited for official church matters.");
		addPlace("PLM Chapel", 332, 617, "CHURCH",
			"The PLM Chapel provides a quiet place of worship for students and faculty of the university. It hosts daily Masses and spiritual activities. The chapel serves as a peaceful sanctuary within the campus grounds.");

				//museum
		addPlace("Destileria Limtuaco Museum", 409, 228, "MUSEUM",
			"Destileria Limtuaco Museum highlights the history of the oldest distillery in the Philippines. Visitors can explore exhibits on liquor production and heritage brewing. The museum offers a unique blend of culture and craftsmanship.");
		addPlace("Bahay Tsinoy, Museum of Chinese in Philippine Life", 184, 315, "MUSEUM",
			"Bahay Tsinoy showcases the contributions of the Chinese community to Philippine history. The museum contains life-sized dioramas, artifacts, and historical displays. It is a popular educational destination for students and tourists.");
		addPlace("Centro de Turismo Intramuros", 165, 389, "MUSEUM",
			"Centro de Turismo Intramuros serves as both a museum and a tourism information center. It provides exhibits showcasing the cultural heritage of Intramuros. Visitors come here to learn about the area's preserved landmarks.");
		addPlace("Museo de Intramuros", 156, 437, "MUSEUM",
			"Museo de Intramuros features extensive collections of ecclesiastical art from the Spanish colonial era. It is housed in a beautifully restored heritage structure. The museum offers deep insight into the religious history of the Philippines.");
		addPlace("Casa Manila", 333, 374, "MUSEUM",
			"Casa Manila is a reconstructed colonial-era home that displays Spanish-period lifestyle. It features period furniture, artworks, and household items. Visitors enjoy exploring the detailed rooms that reflect life during the 19th century.");
		addPlace("San Agustin Convent Museum", 274, 436, "MUSEUM",
			"The San Agustin Convent Museum houses religious artifacts, paintings, and historical documents. It complements the church with its impressive collection of ecclesiastical art. The displays provide insight into the cultural legacy of the Augustinian order.");
		addPlace("Rizal’s Bagumbayan Light and Sound Museum", 245, 546, "MUSEUM",
			"This museum offers an immersive light-and-sound show depicting the life of national hero José Rizal. It features animated scenes and narration that bring history to life. The attraction is highly recommended for educational tours.");


        // refresh sidebar initially
        refreshSidebar(selectedCategory);

        // show window
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ----------------- helpers -----------------
    private void addPlace(String name, int x, int y, String category, String description) {
        Place p = new Place(name, x, y, category.toUpperCase());
        p.description = description;
        places.add(p);
    }

    private Place findNearestPlace() {
        double best = Double.MAX_VALUE;
        Place bestP = null;
        for (Place p : places) {
            if (!p.category.equals(selectedCategory)) continue;
            double dx = p.x - clickX;
            double dy = p.y - clickY;
            double d = Math.hypot(dx, dy);
            if (d < best) { best = d; bestP = p; }
        }
        return bestP;
    }

    private void buildRoute(int panelW, int panelH) {
        currentRoute.clear();
        if (clickX < 0 || nearestPlace == null) return;
        // simple straight-line route: current -> nearest
        int sx = (int)((clickX / (double)imgW) * panelW);
        int sy = (int)((clickY / (double)imgH) * panelH);
        int nx = (int)((nearestPlace.x / (double)imgW) * panelW);
        int ny = (int)((nearestPlace.y / (double)imgH) * panelH);
        currentRoute.add(new Point(sx, sy));
        // add an intermediate midpoint for nicer line (optional)
        currentRoute.add(new Point((sx+nx)/2, (sy+ny)/2));
        currentRoute.add(new Point(nx, ny));
    }

    private void refreshSidebar(String category) {
        sidebarModel.clear();
        for (Place p : places) if (p.category.equals(category)) sidebarModel.addElement(p.name);
    }

    private void updateNearestLabel() {
        if (nearestPlace == null) nearestLabel.setText("Nearest: None");
        else nearestLabel.setText("Nearest: " + nearestPlace.name + " — " + nearestPlace.description);
    }

    // ----------------- data classes -----------------
    private static class Place {
        String name;
        int x, y; // original map coordinates
        String category;
        String description = "(no description)";

        Place(String n, int xx, int yy, String c) { name=n; x=xx; y=yy; category=c.toUpperCase(); }
    }

    // ----------------- main -----------------
    public static void main(String[] args) {
		    // --- LOADING SCREEN FRAME ---
		JWindow loadingScreen = new JWindow();
		loadingScreen.setSize(1500, 760);
		loadingScreen.setLocationRelativeTo(null);

		JPanel loadingPanel = new JPanel();
		loadingPanel.setLayout(new BorderLayout());
		loadingPanel.setBackground(Color.WHITE);

		// --- LOGO (CENTER) ---
		ImageIcon logo = new ImageIcon("intragologo.jpg"); // <- put your logo file here
		JLabel logoLabel = new JLabel(logo, SwingConstants.CENTER);
		loadingPanel.add(logoLabel, BorderLayout.CENTER);

		// --- PROGRESS BAR (BOTTOM) ---
		JProgressBar bar = new JProgressBar();
		bar.setMinimum(0);
		bar.setMaximum(100);
		bar.setStringPainted(true);
		bar.setPreferredSize(new Dimension(1500, 30));
		loadingPanel.add(bar, BorderLayout.SOUTH);

		loadingScreen.add(loadingPanel);
		loadingScreen.setVisible(true);

		// --- LOADING SIMULATION ---
		try {
			for (int i = 0; i <= 100; i++) {
				bar.setValue(i);
				Thread.sleep(20); // loading speed
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// --- CLOSE LOADING SCREEN ---
		loadingScreen.setVisible(false);
		loadingScreen.dispose();
        SwingUtilities.invokeLater(() -> new IntraGo());
    }
}
