export type Customer={id:string;name:string;phone?:string;createdAt:string};
export type EntryType='credit'|'payment';
export type LedgerEntry={id:string;customerId:string;type:EntryType;amountPaise:number;createdAt:string;note?:string};
export interface LedgerStorage{initialize():Promise<void>;listCustomers():Promise<Customer[]>;listEntries(customerId:string):Promise<LedgerEntry[]>;}
