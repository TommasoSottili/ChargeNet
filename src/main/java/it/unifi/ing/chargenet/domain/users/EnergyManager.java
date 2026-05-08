package it.unifi.ing.chargenet.domain.users;

public class EnergyManager  extends User {

    protected  EnergyManager() {
        super();
    }
    public EnergyManager(String name, String password, String email) {
        super(name, password, email, Role.ENERGY_MENAGER);
    }
}
