import javax.swing.*;
import java.awt.*;

public class EsempioGridlayout extends JFrame {


    JPanel riga1= new JPanel();
    JPanel titoloPanel = new JPanel();
    JLabel titolo = new JLabel("Orario Docenti");
    JPanel box = new JPanel();
    JPanel tabella = new JPanel();
    JPanel wrapper = new JPanel();
    Container cFinal = new Container();
    String[][] orario = new String[9][7];
    String[] giorni = {"Lunedi", "Martedi", "Mercoledi", "Giovedi", "Venerdi", "Sabato"};
    String[] ore = {"8:00", "9:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00"};
    JTextField campi = new JTextField();;
    JLabel docente = new JLabel("Docente: ");
    JComboBox sceltaDocente = new JComboBox();
    JButton download = new JButton("PDF");

    public EsempioGridlayout() {
        super("Tabella orari");
        tabella.setLayout(new GridLayout(9,7));

        titoloPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        titolo.setFont(new Font(Font.SERIF, Font.BOLD + Font.ITALIC, 50));
        titolo.setForeground(Color.DARK_GRAY);
        //titoloPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        titoloPanel.add(titolo);

        riga1.setLayout(new BoxLayout(riga1,BoxLayout.X_AXIS));
        riga1.setSize(new Dimension(50,30));
        docente.setFont(new Font(Font.SERIF, Font.BOLD, 20));
        riga1.add(docente);
        riga1.add(sceltaDocente);
        riga1.add(Box.createHorizontalStrut(500));
        riga1.add(download);
        for (int r = 1; r <= 8; ++r){
            orario[r][0] = ore[r - 1];;
            for (int c = 1; c <= 6; ++c) {
                orario[0][c] = giorni[c - 1];
            }
        }

        for (int r = 0; r < 9; ++r) {
            for (int c = 0; c < 7; ++c) {
                if (orario[r][c] == null){
                    JTextField temp = new JTextField();
                    temp.setPreferredSize(new Dimension(120,60));
                    temp.setEditable(false);
                    temp.setBorder(BorderFactory.createLineBorder(Color.black));
                    tabella.add(temp);
                }
                else{
                    JTextField temp2 = new JTextField();

                    temp2.setPreferredSize(new Dimension(120,60));
                    temp2.setEditable(false);
                    if (orario[r][c].equals("Mercoledi")){
                        temp2.setFont(new Font("Arial", Font.BOLD, 15));
                        temp2.setText("   " + orario[r][c]);
                    }
                    else{
                        temp2.setFont(new Font("Arial", Font.BOLD, 15));
                        temp2.setText("    " + orario[r][c]);
                    }
                    temp2.setBorder(BorderFactory.createLineBorder(Color.black));
                    tabella.add(temp2);
                }
            }
        }
        box.setLayout(new BoxLayout(box,BoxLayout.Y_AXIS));
        box.add(titoloPanel);
        box.add(Box.createVerticalStrut(100));
        box.add(riga1);
        box.add(Box.createVerticalStrut(40));
        box.add(tabella);

        wrapper.setLayout(new GridBagLayout());
        wrapper.add(box);

        cFinal.setLayout(new BorderLayout());
        cFinal.add(wrapper, BorderLayout.CENTER);


        add(cFinal,  BorderLayout.CENTER);
        setVisible(true);
        setSize(15000,10000);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    }





}
