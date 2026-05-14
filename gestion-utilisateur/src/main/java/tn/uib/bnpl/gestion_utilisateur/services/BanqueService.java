package tn.uib.bnpl.gestion_utilisateur.services;

import tn.uib.bnpl.gestion_utilisateur.classes.Banque;
import java.util.List;

public interface BanqueService {
    List<Banque> getAll();
    Banque create(Banque banque);
}