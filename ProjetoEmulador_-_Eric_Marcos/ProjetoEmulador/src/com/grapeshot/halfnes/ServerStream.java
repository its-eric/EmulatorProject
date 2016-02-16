/**
 * Classe do servidor que fará o Stream das ROMs para os emuladores que se conectarem
 * nele.
 */

package com.grapeshot.halfnes;

import java.io.*;
import java.net.*;

public class ServerStream {
    
    // Lista de jogos que é passada aos usuários
    final static String listaJogos = "Contra (U):Ghosts'n Goblins (U):Metal Gear (U)";
    
    public static void main(String[] args) {
        try {
            int port = 1234;
            ServerSocket srv = new ServerSocket(port);
            System.out.println("Server listening in " + port + "...");
            
            while (true) {
                // Aguardando conexão com cliente
                Socket cliente = srv.accept();
                System.out.println("Connection established.");
                DataInputStream dis = new DataInputStream(cliente.getInputStream());
                int comando = dis.readInt();
                switch(comando) {
                    case 1: { // Primeira iteração: envia-se a lista de jogos
                        DataOutputStream dos = new DataOutputStream(cliente.getOutputStream());
                        dos.writeUTF(listaJogos); // Lista de jogos é enviada para o cliente
                        
                        break;
                    }
                    case 2: { // Segunda iteração: envia-se a ROM selecionada
                        String nomeRom = dis.readUTF(); // É recebido o nome da ROM escolhida pelo cliente
                        System.out.println("Chosen ROM: " + nomeRom);
                        
                        ObjectOutputStream oos = new ObjectOutputStream(cliente.getOutputStream());
                        oos.flush();
                        
                        new ServerStream().enviarRom(oos, nomeRom); // Inicia-se o envio da ROM
                        
                        break;
                    }
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Método de envio da ROM.
     * @param oos Stream de saída.
     * @param nomeRom String contendo o nome da ROM a ser enviada.
     * @throws Exception 
     */
    public void enviarRom(ObjectOutputStream oos, String nomeRom) throws Exception {
        File rom = new File("C:\\ROMS\\" + nomeRom + ".nes"); // É aberta a ROM armazenada pelo servidor
        FileInputStream fis = new FileInputStream(rom);
        byte[] buffer = new byte[200000];
        
        // Enviando a ROM
        while(true) {  
            int len = fis.read(buffer); // É lido o buffer do arquivo
            if(len == -1) {
                break;
            }
            oos.write(buffer, 0, len); // O buffer do arquivo é enviado ao cliente
        }
        
        oos.close(); // É fechado o Stream de saída
    }
    
}
