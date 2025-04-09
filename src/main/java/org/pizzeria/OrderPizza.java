package org.pizzeria;

public class OrderPizza {
    private String name;
    private int qty;

    public OrderPizza(String name, int qty) {
        this.name = name;
        this.qty = qty;
    }

    public OrderPizza() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }
}
