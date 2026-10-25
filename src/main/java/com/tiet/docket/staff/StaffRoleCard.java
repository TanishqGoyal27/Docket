package com.tiet.docket.staff;

// One card on the staff portal page. "group" only controls the card colour.
public record StaffRoleCard(
        String code, // must match the Role enum, e.g. FINANCE_OFFICER
        String title,
        String description,
        String icon,
        String group) { // "approval", "finance" or "admin"
}