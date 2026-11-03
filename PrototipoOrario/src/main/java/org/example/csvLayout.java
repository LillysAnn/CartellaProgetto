package org.example;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
public class csvLayout extends JFrame{



    JPanel riga1 = new JPanel();
    JLabel percorso = new JLabel("Percorso file csv:");
    JTextField percorsoText = new JTextField();
    JButton ricerca = new JButton("Invio");
    Container cont = getContentPane();

    public csvLayout(){
        riga1.setLayout(new FlowLayout(FlowLayout.LEFT));
        riga1.add(percorso);
        percorsoText.setPreferredSize(new Dimension(90,20));
        riga1.add(percorsoText);
        riga1.add(ricerca);

        add(riga1,BorderLayout.CENTER);
        setSize(305,100);
        setVisible(true);
        setResizable(false);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        ricerca.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            }
        });
    }



}
