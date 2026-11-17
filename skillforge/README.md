# Skill Forge (Fabric, Minecraft 26.3)

ม็อดสร้างสกิลพลังเองในเกม สไตล์ Skill Builder ของ JJS (Roblox)

## วิธีเล่น
- กด **G** เปิดเมนูสร้างสกิล เลือกช่อง/ชื่อ/ประเภท/เอฟเฟกต์/ดาเมจ/ระยะ/คูลดาวน์ แล้วกดบันทึก
- กด **Z X C V** ใช้สกิลช่อง 1-4 (เปลี่ยนปุ่มได้ในตั้งค่าควบคุม หมวด Skill Forge)
- หรือใช้คำสั่ง:
  - `/skill create <ช่อง 1-4> "<ชื่อ>" <ประเภท> <ดาเมจ> <ระยะ> <คูลดาวน์วินาที> <เอฟเฟกต์>`
    - ตัวอย่าง: `/skill create 1 "ไฟพิฆาต" projectile 8 20 3 fire`
  - `/skill cast <ช่อง>`  `/skill delete <ช่อง>`  `/skill list`

ประเภท: `projectile` `dash` `area` `heal`
เอฟเฟกต์: `none` `fire` `slow` `poison` `wither` `knockback`

## วิธี build
1. ติดตั้ง JDK 25
2. ในโฟลเดอร์นี้ ถ้ายังไม่มี gradle wrapper ให้รัน `gradle wrapper --gradle-version 9.6.0`
   (หรือคัดลอก gradlew, gradlew.bat และโฟลเดอร์ gradle/ จากโปรเจกต์ตัวอย่างที่ fabricmc.net/develop/template)
3. `./gradlew build` แล้วเอาไฟล์จาก `build/libs/skillforge-1.0.0.jar` ไปใส่โฟลเดอร์ mods
   พร้อม Fabric API 0.160.5+26.3
4. ทดสอบในเกม: `./gradlew runClient`

## ค่าจำกัดสกิล (กันโกง) แก้ได้ใน Skill.java
ดาเมจสูงสุด 20, ระยะสูงสุด 40 บล็อก, คูลดาวน์ต่ำสุด 0.5 วินาที
