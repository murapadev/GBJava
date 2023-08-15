
package model;


public class ExtendedInstructions {
    static private String[] ExtendedInstructionsDisassembly = new String[]
     {
        "RLC","RRC","RL","RR","SLA","SRA","SWAP","SRL","BIT 0","BIT 1","BIT 2", "BIT 3", "BIT 4",
        "BIT 5", "BIT 7", "RES 0","RES 1", "RES 2", "RES 3", "RES 4", "RES 5", "RES 6", "RES 7"
    };
    static private String[] Targets = new String[]
            {
                    "B","C","D","E","H","L","HL P","A"
            };

    public String dissasembly(byte extendedInstructionCode) {
        return ExtendedInstructionsDisassembly[extendedInstructionCode / 8] + " " + Targets[extendedInstructionCode % 8];
     }

     //TODO: Maybe this will be discontinued.
}
